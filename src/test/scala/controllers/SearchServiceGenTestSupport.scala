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
import cats.{Applicative, Monad, ~>}
import models.CompaniesHouseId
import services._

object SearchServiceGenTestSupport {

  case class TestDbData(
                         reportCounts: Map[CompaniesHouseId, Int],
                         countFiledCallCount: Int = 0,
                         evalDbCallCount: Int = 0
                       )

  case class TestData(
                       searchResults: Map[CompaniesHouseId, CompanySearchResult],
                       dbData: TestDbData
                     )

  type TestF[A] = TestData => (TestData, A)
  type TestDb[A] = TestDbData => (TestDbData, A)

  val evalDb: TestDb ~> TestF = new FunctionK[TestDb, TestF] {
    override def apply[A](fa: TestDb[A]): TestF[A] = {
      testData =>
        val (dbData, a) = fa(testData.dbData)

        (testData.copy(dbData = dbData.copy(evalDbCallCount = dbData.evalDbCallCount + 1)), a)
    }
  }

  implicit val monadF: Monad[TestF] = new Monad[TestF] {
    override def flatMap[A, B](fa: TestF[A])(f: (A) => TestF[B]): TestF[B] = { testData =>
      val (td2, a) = fa(testData)
      f(a)(td2)
    }

    override def tailRecM[A, B](a: A)(f: (A) => TestF[Either[A, B]]): TestF[B] = ???

    override def pure[A](a: A): TestF[A] = s => (s, a)
  }

  implicit val applicativeDb: Applicative[TestDb] = new Applicative[TestDb] {
    override def pure[A](a: A): TestDb[A] = testData => (testData, a)

    override def ap[A, B](ff: TestDb[(A) => B])(fa: TestDb[A]): TestDb[B] = {
      dbData =>
        val (dataA, a) = fa(dbData)
        val (dataB, fb) = ff(dataA)

        (dataB, fb(a))
    }
  }

  val companyId1 = CompaniesHouseId("1")
  val companyName1 = "Company 1"
  val detail1 = CompanySearchResult(companyId1, companyName1, "")

  val testData1 = TestData(Map(companyId1 -> detail1), TestDbData(Map(companyId1 -> 6)))

  val companyId2 = CompaniesHouseId("2")
  val companyName2 = "Company 2"
  val detail2 = CompanySearchResult(companyId2, companyName2, "")

  val testData2 = TestData(
    Map(companyId1 -> detail1, companyId2 -> detail2),
    TestDbData(Map(companyId1 -> 6, companyId2 -> 3))
  )


  object CompanySearchService extends CompanySearchService[TestF] {
    override def searchCompanies(search: String, page: PageNumber, itemsPerPage: PageSize): TestF[PagedResults[CompanySearchResult]] = {
      s =>
        (s, PagedResults.page(s.searchResults.values.toSeq, page, itemsPerPage))
    }

    override def find(companiesHouseId: CompaniesHouseId): TestF[Option[CompanyDetail]] = {
      s =>
        (s, s.searchResults.get(companiesHouseId).map(r => CompanyDetail(r.companiesHouseId, r.companyName)))
    }
  }

  object repo extends ReportRepoStub[TestDb] {
    override def countFiledReports(companiesHouseId: CompaniesHouseId): TestDb[Int] = {
      testData =>
        (testData.copy(countFiledCallCount = testData.countFiledCallCount + 1), testData.reportCounts.getOrElse(companiesHouseId, 0))
    }
  }

}
