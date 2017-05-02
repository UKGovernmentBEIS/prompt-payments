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

import cats.arrow.FunctionK
import org.scalatest.{Matchers, OptionValues, WordSpecLike}
import services._

class SearchServiceGenTest extends WordSpecLike with Matchers with OptionValues {

  import SearchServiceGenTestData._
  import SearchServiceGenTestSupport._

  implicit val idF = FunctionK.id[TestF]

  val sut = new SearchServiceGen[TestF, TestF, TestDb](CompanySearchService, repo)

  private val page1 = PageNumber(1)
  private val size25 = PageSize(25)

  private val emptySearchString = ""

  "SearchService.doSearch" should {
    "return correct results when query string is empty for one company" in {
      val ResultsWithCounts(results, counts) = sut.doSearch(emptySearchString, page1, size25)(testData1)._2

      results shouldBe PagedResults(Seq(detail1), page1, size25, 1)
      counts.get(companyId1).value shouldBe 6
    }

    "ensure evalDb is called only once when there are multiple companies in the results" in {
      val (dataOut, ResultsWithCounts(results, counts)) = sut.doSearch(emptySearchString, page1, size25)(testData2)

      results shouldBe PagedResults(Seq(detail1, detail2), page1, size25, 2)
      counts.get(companyId1).value shouldBe 6
      counts.get(companyId2).value shouldBe 3

      // There are two companies so `countFiled` should be called twice...
      dataOut.dbData.countFiledCallCount shouldBe 2

      // ...but the db calls should have been merged into one session so `evalDb` should only
      // be called once.
      dataOut.dbData.evalDbCallCount shouldBe 1
    }
  }
}


