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
import cats.{Monad, ~>}
import models.CompaniesHouseId
import services._

object SearchServiceGenTestSupport {

  import SearchServiceGenTestData._

  // Accept data of type D and return a (possibly updated) instance of D and a value A
  type TestData[D, A] = D => (D, A)
  type TestF[A] = TestData[SearchTestData, A]
  type TestDb[A] = TestData[TestDbData, A]

  implicit def monadD[D] : Monad[TestData[D, ?]] = new Monad[TestData[D, ?]] {
    override def pure[A](a: A): TestData[D, A] = d => (d, a)

    override def flatMap[A, B](fa: TestData[D, A])(f: (A) => TestData[D, B]): TestData[D, B] = {
      val fm = fa.andThen { case (td, a) => f(a)(td) }
      d => fm(d)
    }

    override def tailRecM[A, B](a: A)(f: (A) => TestData[D, Either[A, B]]): TestData[D, B] = ???
  }

  val evalDb: TestDb ~> TestF = new FunctionK[TestDb, TestF] {
    override def apply[A](fa: TestDb[A]): TestF[A] = {
      testData =>
        val (dbData, a) = fa(testData.dbData)

        (testData.copy(dbData = dbData.copy(evalDbCallCount = dbData.evalDbCallCount + 1)), a)
    }
  }

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
