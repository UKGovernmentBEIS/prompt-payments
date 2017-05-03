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

import models.CompaniesHouseId
import services.CompanySearchResult

object SearchServiceGenTestData {

  case class RepoTestData(
                           reportCounts: Map[CompaniesHouseId, Int],
                           countFiledCallCount: Int = 0,
                           evalDbCallCount: Int = 0
                         )

  case class CompanyTestData(searchResults: Map[CompaniesHouseId, CompanySearchResult])

  case class SearchTestData(
                             companyData: CompanyTestData,
                             dbData: RepoTestData
                           )

  val companyId1 = CompaniesHouseId("1")
  val companyName1 = "Company 1"
  val detail1 = CompanySearchResult(companyId1, companyName1, "")

  val testData1 = SearchTestData(CompanyTestData(Map(companyId1 -> detail1)), RepoTestData(Map(companyId1 -> 6)))

  val companyId2 = CompaniesHouseId("2")
  val companyName2 = "Company 2"
  val detail2 = CompanySearchResult(companyId2, companyName2, "")

  val testData2 = SearchTestData(
    CompanyTestData(Map(companyId1 -> detail1, companyId2 -> detail2)),
    RepoTestData(Map(companyId1 -> 6, companyId2 -> 3))
  )
}
