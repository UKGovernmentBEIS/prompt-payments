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

package services.live

import javax.inject.Inject

import dbrows.ConfirmationPendingRow
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat
import play.api.Logger
import repos.{ConfirmationRepo, FiledReport}
import services._
import uk.gov.service.notify.NotificationClientException
import views.html.ReportNum

import scala.concurrent.{ExecutionContext, Future}

class ConfirmationDeliveryServiceImpl @Inject()(confirmationRepo: ConfirmationRepo[Future], notifyService: NotifyService[Future])(implicit ec: ExecutionContext)
  extends ConfirmationDeliveryService[Future] {
  val df = DateTimeFormat.forPattern("d MMMM YYYY")

  def attemptDelivery: Future[Option[DeliveryOutcome]] = {
    confirmationRepo.findUnconfirmedAndLock().flatMap {
      case Some((confirmation, report)) => attemptToSend(confirmation, report).map(Some(_))
      case _ => Future.successful(None)
    }
  }

  private def attemptToSend(confirmation: ConfirmationPendingRow, report: FiledReport): Future[DeliveryOutcome] = {
    notifyService.sendEmail(confirmation.emailAddress, buildParams(confirmation, report)).flatMap { response =>
      confirmationRepo.confirmationSent(report.header.id, LocalDateTime.now, response)
        .map(_ => ConfirmationSent(report.header.id))
    }.recoverWith {
      case nex: NotificationClientException =>
        confirmationRepo.confirmationFailed(report.header.id, LocalDateTime.now, nex)
          .map(_ => ConfirmationFailed(report.header.id))

      case ex: Exception =>
        Logger.error("Exception sending email", ex)
        throw ex
    }
  }

  private def buildParams(row: ConfirmationPendingRow, report: FiledReport) = {
    Map[String, String](
      "companyName" -> report.header.companyName,
      "companieshouseidentifier" -> report.header.companyId.id,
      "startdate" -> df.print(report.period.startDate),
      "enddate" -> df.print(report.period.endDate),
      "reportid" -> ReportNum(row.reportId),
      "reporturl" -> row.url
    )
  }
}
