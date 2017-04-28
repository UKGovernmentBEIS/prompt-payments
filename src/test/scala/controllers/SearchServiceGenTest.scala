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

import cats.{Monad, ~>}
import cats.arrow.FunctionK
import models.CompaniesHouseId
import org.scalatest.{Matchers, OptionValues, WordSpecLike}
import services._

class SearchServiceGenTest extends WordSpecLike with Matchers with OptionValues {

  import SearchServiceGenTest._

  val sut = new SearchServiceGen[TestF, TestF](companySearchService, repo, evalF)

  "SearchService.searchResults" should {
    "return correct search results" in {
      sut.searchResults(PageNumber(1), PageSize(25), "")(testData)._2 shouldBe PagedResults(Seq(detail1), PageNumber(1), PageSize(25), 1)
    }
  }

  "SearchService.countReports" should {
    "return correct report count" in {
      sut.countReports(companyId1)(testData)._2 shouldBe 6
    }

    "return report count of 0 when company id is not found" in {
      sut.countReports(CompaniesHouseId("not found"))(testData)._2 shouldBe 0
    }
  }

  "SearchService.buildResults" should {
    "return correct results" in {
      val ResultsWithCounts(results, counts) = sut.buildResults(PageNumber(1), PageSize(25), "")(testData)._2
      results.value.items shouldBe Seq(detail1)
      results.value.pageCount shouldBe 1
      results.value.pageNumber.value shouldBe 1
      results.value.totalResults shouldBe 1

      counts.get(companyId1).value shouldBe 6
    }
  }

  "SearchService.doSearch" should {
    "return correct results" in {
      val (q, ResultsWithCounts(results, counts)) = sut.doSearch(Some(""), PageNumber(1), PageSize(25))(testData)._2

      q shouldBe ""
      results.value.items shouldBe Seq(detail1)
      results.value.pageCount shouldBe 1
      results.value.pageNumber.value shouldBe 1
      results.value.totalResults shouldBe 1

      counts.get(companyId1).value shouldBe 6
    }
  }
}

object SearchServiceGenTest {

  case class TestData(
                       searchResults: Map[CompaniesHouseId, CompanySearchResult],
                       reportCounts: Map[CompaniesHouseId, Int]
                     )

  val evalF : TestF ~> TestF = FunctionK.id[TestF]

  implicit val monadF: Monad[TestF] = new Monad[TestF] {
    override def flatMap[A, B](fa: TestF[A])(f: (A) => TestF[B]): TestF[B] = { testData =>
      val (td2, a) = fa(testData)
      f(a)(td2)
    }

    override def tailRecM[A, B](a: A)(f: (A) => TestF[Either[A, B]]): TestF[B] = ???

    override def pure[A](x: A): TestF[A] = s => (s, x)
  }

  private val companyId1 = CompaniesHouseId("1")
  private val companyName1 = "Company 1"

  private val detail1 = CompanySearchResult(companyId1, companyName1, "")
  val testData = TestData(Map(companyId1 -> detail1), Map(companyId1 -> 6))

  type TestF[A] = TestData => (TestData, A)

  object companySearchService extends CompanySearchService[TestF] {
    override def searchCompanies(search: String, page: PageNumber, itemsPerPage: PageSize): TestF[PagedResults[CompanySearchResult]] = {
      s =>
        (s, PagedResults.page(s.searchResults.values.toSeq, page, itemsPerPage))
    }

    override def find(companiesHouseId: CompaniesHouseId): TestF[Option[CompanyDetail]] = {
      s =>
        (s, s.searchResults.get(companiesHouseId).map(r => CompanyDetail(r.companiesHouseId, r.companyName)))
    }
  }

  object repo extends ReportRepoStub[TestF] {
    override def countFiledReports(companiesHouseId: CompaniesHouseId): TestF[Int] = {
      testData => (testData, testData.reportCounts.getOrElse(companiesHouseId, 0))
    }
  }

}
