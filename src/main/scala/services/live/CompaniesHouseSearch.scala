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

import java.util.Base64
import javax.inject.Inject

import config.CompaniesHouseConfig
import models.CompaniesHouseId
import play.api.Logger
import play.api.libs.ws.WSClient
import services._

import scala.concurrent.{ExecutionContext, Future}

class CompaniesHouseSearch @Inject()(val ws: WSClient, config: CompaniesHouseConfig)(implicit val ec: ExecutionContext)
  extends RestService with CompanySearchService[Future] {

  import CompaniesHouseModels._

  private val basicAuth = "Basic " + new String(Base64.getEncoder.encode(config.apiKey.getBytes))

  def targetScope(companiesHouseId: CompaniesHouseId): String = s"https://api.companieshouse.gov.uk/company/${companiesHouseId.id}"

  override def searchCompanies(search: String, page: PageNumber, itemsPerPage: PageSize): Future[PagedResults[CompanySearchResult]] = {
    val s = views.html.helper.urlEncode(search)
    val startIndex = (page.value - 1) * itemsPerPage.value
    val url = s"https://api.companieshouse.gov.uk/search/companies?q=$s&items_per_page=$itemsPerPage&start_index=$startIndex"
    val start = System.currentTimeMillis()

    get[ResultsPage](url, basicAuth).map { resultsPage =>
      val t = System.currentTimeMillis() - start
      Logger.debug(s"Companies house search took ${t}ms")
      val results = resultsPage.items.map(i => CompanySearchResult(i.company_number, i.title, i.address_snippet))
      PagedResults(results, PageNumber(resultsPage.page_number), PageSize(resultsPage.items_per_page), resultsPage.total_results)
    }
  }

  override def find(companiesHouseId: CompaniesHouseId): Future[Option[CompanyDetail]] = {
    val id = views.html.helper.urlEncode(companiesHouseId.id)
    val url = targetScope(companiesHouseId)

    getOpt[CompaniesHouseFindResult](url, basicAuth).map(_.map(r => CompanyDetail(r.company_number, r.company_name)))
  }
}
