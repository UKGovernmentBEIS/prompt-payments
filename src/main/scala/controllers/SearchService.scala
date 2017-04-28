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
import play.twirl.api.Html
import repos.ReportRepo
import services._
import slick.dbio.DBIO

import scala.concurrent.{ExecutionContext, Future}

trait SearchService[F[_]] {
  type ResultsPageFunction = (String, Option[PagedResults[CompanySearchResult]], Map[CompaniesHouseId, Int]) => Html

  def doSearch(query: Option[String], pageNumber: PageNumber, itemsPerPage: PageSize, resultsPage: ResultsPageFunction): F[Html]
}

case class ResultsWithCounts(results: Option[PagedResults[CompanySearchResult]], counts: Map[CompaniesHouseId, Int])

object ResultsWithCounts {
  val empty = ResultsWithCounts(None, Map.empty)
}

class SearchServiceGen[F[_] : Monad, DbEffect[_]](companySearch: CompanySearchService[F], reportRepo: ReportRepo[DbEffect], evalDb: DbEffect ~> F)
  extends SearchService[F] {
  override def doSearch(query: Option[String], pageNumber: PageNumber, itemsPerPage: PageSize, resultsPage: ResultsPageFunction): F[Html] = {
    queryResults(query, pageNumber, itemsPerPage).map { case (q, rc) => resultsPage(q, rc.results, rc.counts) }
  }

  private[controllers] def queryResults(query: Option[String], pageNumber: PageNumber, itemsPerPage: PageSize): F[(String, ResultsWithCounts)] = {
    query match {
      case Some(q) => buildResults(pageNumber, itemsPerPage, q).map {
        results => (q, results)
      }

      case None => implicitly[Applicative[F]].pure(("", ResultsWithCounts.empty))
    }
  }

  private[controllers] def buildResults(pageNumber: PageNumber, itemsPerPage: PageSize, q: String): F[ResultsWithCounts] = {
    searchResults(pageNumber, itemsPerPage, q).flatMap { results =>
      results.items.map(_.companiesHouseId).toList
        .traverse(id => countReports(id).map(id -> _))
        .map(counts => ResultsWithCounts(Some(results), Map(counts: _*)))
    }
  }

  private[controllers] def searchResults(pageNumber: PageNumber, itemsPerPage: PageSize, q: String): F[PagedResults[CompanySearchResult]] =
    companySearch.searchCompanies(q, pageNumber, itemsPerPage)

  private[controllers] def countReports(companiesHouseId: CompaniesHouseId): F[Int] =
    evalDb(reportRepo.countFiledReports(companiesHouseId))
}

class SearchServiceImpl @Inject()(companySearch: CompanySearchService[Future], reportRepo: ReportRepo[DBIO], evalDb: DBIO ~> Future)(implicit ec: ExecutionContext)
  extends SearchServiceGen[Future, DBIO](companySearch, reportRepo, evalDb)(catsStdInstancesForFuture)
