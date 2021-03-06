/*
 * Copyright (C) 2017  Department for Business, Energy and Industrial Strategy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package slicks.repos

import javax.inject.Inject

import dbrows._
import models.ReportId
import org.joda.time.LocalDateTime
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig}
import services.{ConfirmationService, Report}
import slick.dbio.Effect.Write
import slick.jdbc.JdbcProfile
import slicks.modules.{ConfirmationModule, CoreModule, ReportModule}
import uk.gov.service.notify.{NotificationClientException, SendEmailResponse}
import utils.{NotificationClientErrorProcessing, PermanentFailure, TransientFailure}

import scala.concurrent.{ExecutionContext, Future}

class ConfirmationTable @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends ConfirmationService
    with CoreModule
    with ConfirmationModule
    with ReportModule
    with ReportQueries
    with HasDatabaseConfig[JdbcProfile] {

  override lazy val dbConfig = dbConfigProvider.get[JdbcProfile]

  import profile.api._

  private val unconfirmedReportsQ = reportTable
    .joinLeft(confirmationPendingTable).on((r, p) => r.id === p.reportId)
    .joinLeft(confirmationSentTable).on { case ((r, p), s) => r.id === s.reportId }
    .joinLeft(confirmationFailedTable).on { case (((r, p), s), f) => r.id === f.reportId }
    .map {
      case (((r, p), s), f) => (r, p, s, f)
    }.filter { case (r, p, s, f) => p.isEmpty && s.isEmpty && f.isEmpty }
    .map { case (r, p, s, f) => r }

  private val unconfirmedReportsC = Compiled(unconfirmedReportsQ)

  override def createPendingConfirmations(urlFunction: ReportId => String): Future[Unit] = db.run {
    unconfirmedReportsC.result.flatMap { rs => {
      val confirmationPendingRows = rs.map { r =>
        ConfirmationPendingRow(r.id, r.confirmationEmailAddress, urlFunction(r.id), 0, None, None, None)
      }
      confirmationPendingTable ++= confirmationPendingRows
    }.map(_ => ())
    }.transactionally
  }

  override def findUnconfirmedAndLock(): Future[Option[(ConfirmationPendingRow, Report)]] = db.run {
    val lockTimeout = LocalDateTime.now().minusSeconds(30)

    val q = for {
      c <- confirmationPendingTable if c.lockedAt.isEmpty || c.lockedAt < lockTimeout
      r <- activeReportQuery if r._1.id === c.reportId
    } yield (c, r)

    val action = q.result.headOption.map(_.map { case (c, r) => (c, Report.apply(r)) })

    action.flatMap {
      case Some((c, r)) =>
        confirmationPendingTable.filter(_.reportId === c.reportId).map(_.lockedAt)
          .update(Some(LocalDateTime.now())).map(_ => Some((c, r)))

      case None => DBIO.successful(None)
    }.transactionally
  }

  override def confirmationSent(reportId: ReportId, when: LocalDateTime, response: SendEmailResponse): Future[Unit] = {
    disposeOfPending(reportId, when, disposeToSent(response))
  }

  override def confirmationFailed(reportId: ReportId, when: LocalDateTime, ex: NotificationClientException): Future[Unit] = {
    val failure = NotificationClientErrorProcessing.parseNotificationMessage(ex.getHttpResult, ex.getMessage) match {
      case Some(err) => NotificationClientErrorProcessing.processError(err)
      case None      => PermanentFailure(0, s"Unable to parse error body: ${ex.getMessage}")
    }

    failure match {
      case f: PermanentFailure => failPermanently(reportId, when, f)
      case f: TransientFailure => failTransiently(reportId, when, f)
    }
  }

  private def pendingQ(reportId: Rep[ReportId]) = confirmationPendingTable.filter(_.reportId === reportId)

  val pendingC = Compiled(pendingQ _)

  type DisposalFunction = (ConfirmationPendingRow, LocalDateTime) => DBIOAction[Any, NoStream, Write]

  private def disposeOfPending(reportId: ReportId, when: LocalDateTime, disposal: DisposalFunction): Future[Unit] = db.run {
    pendingC(reportId).result.headOption.flatMap {
      case Some(pending) => for {
        _ <- disposal(pending, when)
        _ <- pendingC(reportId).delete
      } yield ()

      case None => DBIO.successful(())
    }.map(_ => ()).transactionally
  }

  private def disposeToFailed(f: PermanentFailure): DisposalFunction = { (pending, when) =>
    val failedRow = ConfirmationFailedRow(pending.reportId, pending.emailAddress, f.statusCode, f.message, when)
    confirmationFailedTable += failedRow
  }

  private def disposeToSent(response: SendEmailResponse): DisposalFunction = { (pending, when) =>
    val sentRow = ConfirmationSentRow(pending.reportId, pending.emailAddress, response.getBody, response.getNotificationId.toString, when)
    confirmationSentTable += sentRow
  }

  private def failPermanently(reportId: ReportId, when: LocalDateTime, f: PermanentFailure): Future[Unit] =
    disposeOfPending(reportId, when, disposeToFailed(f))

  private def failTransiently(reportId: ReportId, when: LocalDateTime, f: TransientFailure): Future[Unit] = db.run {
    pendingC(reportId).result.headOption.flatMap {
      case Some(pending) =>
        val updatedRow = pending.copy(lastErrorState = Some(f.statusCode), lastErrorText = Some(f.message), retryCount = pending.retryCount + 1)
        pendingC(reportId).update(updatedRow).map(_ => ())

      case None => DBIO.successful(())
    }.map(_ => ()).transactionally
  }
}
