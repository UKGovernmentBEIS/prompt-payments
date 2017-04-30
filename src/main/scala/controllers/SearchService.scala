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

trait SearchService[F[_]] {
  def doSearch(query: String, pageNumber: PageNumber, itemsPerPage: PageSize): F[ResultsWithCounts]
}

case class ResultsWithCounts(results: PagedResults[CompanySearchResult], counts: Map[CompaniesHouseId, Int])

class SearchServiceGen[F[_] : Monad, DbEffect[_] : Applicative](companySearch: CompanySearchService[F],
                                                                reportRepo: ReportRepo[DbEffect],
                                                                evalDb: DbEffect ~> F)
  extends SearchService[F] {
  override def doSearch(q: String, pageNumber: PageNumber, itemsPerPage: PageSize): F[ResultsWithCounts] = {
    companySearch.searchCompanies(q, pageNumber, itemsPerPage).flatMap { results =>
      evalDb {
        results.items.toList.traverse(item => reportRepo.countFiledReports(item.companiesHouseId).map(count => item.companiesHouseId -> count))
      }.map(counts => ResultsWithCounts(results, Map(counts: _*)))
    }
  }
}

class SearchServiceImpl @Inject()(companySearch: CompanySearchService[Future], reportRepo: ReportRepo[DBIO], evalDb: DBIO ~> Future)(implicit ec: ExecutionContext)
  extends SearchServiceGen[Future, DBIO](companySearch, reportRepo, evalDb)(catsStdInstancesForFuture, DBIOMonad.dbioMonadInstance)
