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

import com.github.tminglei.slickpg.PgDateSupportJoda
import dbrows._
import forms.report.{ReportFormModel, ReportReviewModel}
import models.{CompaniesHouseId, ReportId}
import org.joda.time.LocalDate
import org.reactivestreams.Publisher
import play.api.db.slick.DatabaseConfigProvider
import repos.{FiledReport, Report, ReportRepo}
import slicks.DBBinding
import slicks.helpers.RowBuilders
import slicks.modules.{ConfirmationModule, ReportModule}

import scala.concurrent.ExecutionContext

class ReportTable @Inject()(val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends DBBinding
    with ReportRepo[slick.dbio.DBIO]
    with ReportModule
    with ConfirmationModule
    with ReportQueries
    with PgDateSupportJoda
    with RowBuilders {

  import api._

  def reportByIdQ(id: Rep[ReportId]) = reportQuery.filter(_._1.id === id)

  val reportByIdC = Compiled(reportByIdQ _)

  def find(id: ReportId): DBIO[Option[Report]] = reportByIdC(id).result.headOption.map(_.map(Report.tupled))

  def filedReportByIdQ(id: Rep[ReportId]) = filedReportQuery.filter(_._1.id === id)

  val filedReportByIdC = Compiled(filedReportByIdQ _)

  def findFiled(id: ReportId): DBIO[Option[FiledReport]] = filedReportByIdC(id).result.headOption.map(_.map(FiledReport.tupled))

  def reportByCoNoQ(cono: Rep[CompaniesHouseId]) = reportQuery.filter(_._1.companyId === cono)

  val reportByCoNoC = Compiled(reportByCoNoQ _)

  def byCompanyNumber(companiesHouseId: CompaniesHouseId): DBIO[Seq[Report]] =
    reportByCoNoC(companiesHouseId).result.map(_.map(Report.tupled))

  def filedReportsByCoNoQ(id: Rep[CompaniesHouseId]) = filedReportQuery.filter(_._1.companyId === id)

  override def countFiledReports(companiesHouseId: CompaniesHouseId): DBIO[Int] =
    filedReportsByCoNoQ(companiesHouseId).countDistinct.result

  /**
    * Code to adjust fetchSize on Postgres driver taken from:
    * https://engineering.sequra.es/2016/02/database-streaming-on-play-with-slick-from-publisher-to-chunked-result/
    */
  def list(cutoffDate: LocalDate): Publisher[FiledReport] = {
    val disableAutocommit = SimpleDBIO(_.connection.setAutoCommit(false))
    val action = filedReportQueryC.result.withStatementParameters(fetchSize = 10000)

    db.stream(disableAutocommit andThen action).mapResult(FiledReport.tupled)
  }

  override def create(
                       confirmedBy: String,
                       companiesHouseId: CompaniesHouseId,
                       companyName: String,
                       report: ReportFormModel,
                       review: ReportReviewModel,
                       confirmationEmailAddress: String,
                       reportUrl: ReportId => String
                     ): DBIO[ReportId] = {
    val header = ReportHeaderRow(ReportId(0), companyName, companiesHouseId, new LocalDate(), new LocalDate())
    for {
      reportId <- reportHeaderTable.returning(reportHeaderTable.map(_.id)) += header
      _ <- reportPeriodTable += buildPeriodRow(report, reportId)
      _ <- paymentTermsTable += buildPaymentTermsRow(report, reportId)
      _ <- paymentHistoryTable += buildPaymentHistoryRow(report, reportId)
      _ <- otherInfoTable += buildOtherInfoRow(report, reportId)
      _ <- filingTable += buildFilingRow(review, reportId, confirmationEmailAddress)
      _ <- confirmationPendingTable += ConfirmationPendingRow(reportId, confirmationEmailAddress, reportUrl(reportId), 0, None, None, None)
    } yield reportId
  }.transactionally
}
