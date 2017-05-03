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
import monocle.macros.GenLens
import services._

object SearchServiceGenTestSupport {

  import SearchServiceGenTestData._

  // Accept data of type D and return a (possibly updated) instance of D and a value A
  type TestData[D, A] = D => (D, A)

  type TestF[A] = TestData[SearchTestData, A]
  type TestRest[A] = TestData[CompanyTestData, A]
  type TestDb[A] = TestData[RepoTestData, A]

  implicit def monadD[D]: Monad[TestData[D, ?]] = new Monad[TestData[D, ?]] {
    override def pure[A](a: A): TestData[D, A] = d => (d, a)

    override def flatMap[A, B](fa: TestData[D, A])(f: (A) => TestData[D, B]): TestData[D, B] = {
      val fm = fa.andThen { case (td, a) => f(a)(td) }
      d => fm(d)
    }

    override def tailRecM[A, B](init: A)(f: (A) => TestData[D, Either[A, B]]): TestData[D, B] = {
      data =>
        f(init)(data) match {
          case (d, Right(b)) => (d, b)
          case (d, Left(a)) => tailRecM(a)(f)(d)
        }
    }
  }

  private val dbDataLens = GenLens[SearchTestData](_.dbData)
  private val restDataLens = GenLens[SearchTestData](_.companyData)
  private val evalDbCountLens = GenLens[RepoTestData](_.evalDbCallCount)
  private val countFiledLens = GenLens[RepoTestData](_.countFiledCallCount)

  private def countFiledCalled(dbData: RepoTestData) = countFiledLens.modify(_ + 1)(dbData)

  private def evalDbCalled[A](dbData: RepoTestData) = evalDbCountLens.modify(_ + 1)(dbData)

  implicit val evalDb: TestDb ~> TestF = new FunctionK[TestDb, TestF] {
    override def apply[A](fa: TestDb[A]): TestF[A] = {
      testData =>
        fa.andThen {
          case (d, a) => (dbDataLens.set(evalDbCalled(d))(testData), a)
        }(testData.dbData)
    }
  }

 implicit val evalRest: TestRest ~> TestF = new FunctionK[TestRest, TestF] {
    override def apply[A](fa: TestRest[A]): TestF[A] = {
      testData =>
        fa.andThen {
          case (d, a) => (restDataLens.set(d)(testData), a)
        }(testData.companyData)
    }
  }

  object repo extends ReportRepoStub[TestDb] {
    override def countFiledReports(companiesHouseId: CompaniesHouseId): TestDb[Int] = {
      dbData => (countFiledCalled(dbData), dbData.reportCounts.getOrElse(companiesHouseId, 0))
    }
  }

  object CompanySearchService extends CompanySearchService[TestRest] {
    override def searchCompanies(search: String, page: PageNumber, itemsPerPage: PageSize): TestRest[PagedResults[CompanySearchResult]] = {
      s =>
        (s, PagedResults.page(s.searchResults.values.toSeq, page, itemsPerPage))
    }

    override def find(companiesHouseId: CompaniesHouseId): TestRest[Option[CompanyDetail]] = {
      s =>
        (s, s.searchResults.get(companiesHouseId).map(r => CompanyDetail(r.companiesHouseId, r.companyName)))
    }
  }

}
