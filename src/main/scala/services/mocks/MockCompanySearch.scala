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

package services.mocks

import javax.inject.Inject

import models.CompaniesHouseId
import org.scalactic.TripleEquals._
import services.{CompanyDetail, CompanySearchResult, CompanySearchService, PagedResults}

import scala.concurrent.{ExecutionContext, Future}

class MockCompanySearch @Inject()(implicit ec: ExecutionContext) extends CompanySearchService[Future] {

  val companies: Seq[CompanySearchResult] = Seq(
    CompanySearchResult(CompaniesHouseId("000000001"), "The Testing Company", "1 Testing Way, Mockington, Stubshire"),
    CompanySearchResult(CompaniesHouseId("000000002"), "Another company", "1 Any Other Way, Stubbsville, Mockshire, ST13 3MO")
  )

  override def searchCompanies(search: String, page: Int, itemsPerPage: Int): Future[PagedResults[CompanySearchResult]] = Future {
    PagedResults.page(companies.filter(_.companyName.toLowerCase.contains(search.toLowerCase)), 1)
  }

  override def find(companiesHouseId: CompaniesHouseId): Future[Option[CompanyDetail]] = Future {
    companies.find(_.companiesHouseId === companiesHouseId).map(r => CompanyDetail(r.companiesHouseId, r.companyName))
  }
}
