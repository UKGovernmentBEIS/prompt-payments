package controllers

import forms.report.{ReportFormModel, ReportReviewModel}
import models.{CompaniesHouseId, ReportId}
import org.joda.time.LocalDate
import org.reactivestreams.Publisher
import repos.{FiledReport, Report, ReportRepo}

trait ReportRepoStub[F[_]] extends ReportRepo[F] {
  override def find(id: ReportId): F[Option[Report]] = ???

  override def findFiled(id: ReportId): F[Option[FiledReport]] = ???

  override def byCompanyNumber(companiesHouseId: CompaniesHouseId): F[Seq[Report]] = ???

  override def countFiledReports(companiesHouseId: CompaniesHouseId): F[Int] = ???

  override def list(cutoffDate: LocalDate): Publisher[FiledReport] = ???

  override def create(confirmedBy: String,
                      companiesHouseId: CompaniesHouseId,
                      companyName: String,
                      reportFormModel: ReportFormModel,
                      review: ReportReviewModel,
                      confirmationEmailAddress: String,
                      reportUrl: (ReportId) => String): F[ReportId] = ???
}
