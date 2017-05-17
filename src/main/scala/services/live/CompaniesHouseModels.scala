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

import com.wellfactored.playbindings.ValueClassFormats
import models.CompaniesHouseId
import play.api.libs.json.Json

/**
  * Contains the models for reading json from the companies house api, hence the non-scala naming
  * conventions for the case class parameters
  */
object CompaniesHouseModels {

  case class CompaniesHouseSearchResult(company_number: CompaniesHouseId, title: String, address_snippet: Option[String])

  object CompaniesHouseSearchResult extends ValueClassFormats {
    implicit def fmt = Json.format[CompaniesHouseSearchResult]
  }

  case class CompaniesHouseFindResult(company_number: CompaniesHouseId, company_name: String)

  object CompaniesHouseFindResult extends ValueClassFormats {
    implicit def fmt = Json.format[CompaniesHouseFindResult]
  }


  case class ResultsPage(
                          page_number: Int,
                          start_index: Int,
                          items_per_page: Int,
                          total_results: Int,
                          items: List[CompaniesHouseSearchResult]
                        )

  object ResultsPage {
    implicit val fmt = Json.format[ResultsPage]
  }

}
