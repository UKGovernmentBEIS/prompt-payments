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

import javax.inject.Inject

import cats.arrow.FunctionK
import cats.instances.future._
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.{Applicative, Monad, ~>}
import models.CompaniesHouseId
import repos.ReportRepo
import services._
import slick.dbio.DBIO
import slicks.repos.DBIOMonad

import scala.concurrent.{ExecutionContext, Future}

case class ResultsWithCounts(results: PagedResults[CompanySearchResult], counts: Map[CompaniesHouseId, Int])

trait SearchService[F[_]] {
  def doSearch(query: String, pageNumber: PageNumber, itemsPerPage: PageSize): F[ResultsWithCounts]
}

class SearchServiceGen[F[_] : Monad, RestEffect[_], DbEffect[_] : Applicative](companySearch: CompanySearchService[RestEffect],
                                                                               reportRepo: ReportRepo[DbEffect])
                                                                              (implicit evalRest: RestEffect ~> F,
                                                                               evalDb: DbEffect ~> F)
  extends SearchService[F] {

  def run[H[_], A](h: => H[A])(implicit eval: H ~> F): F[A] = eval(h)

  override def doSearch(q: String, pageNumber: PageNumber, itemsPerPage: PageSize): F[ResultsWithCounts] = {
    for {
      results <- run(companySearch.searchCompanies(q, pageNumber, itemsPerPage))
      counts <- run(countReports(results.items.toList))
    } yield ResultsWithCounts(results, Map(counts: _*))
  }

  private def countReports(items: List[CompanySearchResult]): DbEffect[List[(CompaniesHouseId, Int)]] = {
    items.traverse(item => reportRepo.countFiledReports(item.companiesHouseId).map(count => item.companiesHouseId -> count))
  }
}

class SearchServiceImpl @Inject()(companySearch: CompanySearchService[Future], reportRepo: ReportRepo[DBIO], evalDb: DBIO ~> Future)(
  implicit ec: ExecutionContext
)
  extends SearchServiceGen[Future, Future, DBIO](
    companySearch,
    reportRepo)(
    catsStdInstancesForFuture,
    DBIOMonad.dbioMonadInstance,
    FunctionK.id[Future],
    evalDb)
