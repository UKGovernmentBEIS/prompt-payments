package controllers

import cats.Monad
import cats.arrow.FunctionK
import models.CompaniesHouseId
import org.scalatest.{Matchers, WordSpecLike}
import services.{CompanyDetail, CompanySearchResult, CompanySearchService, PagedResults}

class SearchHelperGenTest extends WordSpecLike with Matchers {

  import SearchHelperGenTest._

  val sut = new SearchHelperGen[TestF, TestF](companySearchService, repo, evalF)

  "SearchHelper" should {
    "return correct search results" in {
      sut.searchResults(None, None, "")(testData)._2 shouldBe PagedResults(Seq(detail1), 1, 25, 1)
    }

    "return correct report count" in {
      sut.countReports(companyId1)(testData)._2 shouldBe 6
    }
  }
}

object SearchHelperGenTest {

  case class TestData(
                       searchResults: Map[CompaniesHouseId, CompanySearchResult],
                       reportCounts: Map[CompaniesHouseId, Int]
                     )

  val evalF = FunctionK.id[TestF]

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
    override def searchCompanies(search: String, page: Int, itemsPerPage: Int): TestF[PagedResults[CompanySearchResult]] = {
      s =>
        val results = s.searchResults.values.toSeq
        (s, PagedResults(results, page, itemsPerPage, results.length))
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
