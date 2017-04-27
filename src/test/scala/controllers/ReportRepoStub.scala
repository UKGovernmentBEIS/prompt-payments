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
