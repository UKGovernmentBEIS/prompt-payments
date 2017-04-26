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
import services.{CompanySearchResult, CompanySearchService, PagedResults}
import slick.dbio.DBIO

import scala.concurrent.{ExecutionContext, Future}

trait SearchHelper[F[_]] {
  type ResultsPageFunction = (String, Option[PagedResults[CompanySearchResult]], Map[CompaniesHouseId, Int]) => Html

  def doSearch(query: Option[String], pageNumber: Option[Int], itemsPerPage: Option[Int], resultsPage: ResultsPageFunction): F[Html]
}

class SearchHelperGen[F[_] : Monad, DbEffect[_]](companySearch: CompanySearchService[F], reportRepo: ReportRepo[DbEffect], evalDb: DbEffect ~> F)
  extends SearchHelper[F] {
  override def doSearch(query: Option[String], pageNumber: Option[Int], itemsPerPage: Option[Int], resultsPage: ResultsPageFunction): F[Html] = {
    query match {
      case Some(q) => buildResults(pageNumber, itemsPerPage, q).map {
        case (results, counts) => resultsPage(q, Some(results), counts)
      }

      case None => implicitly[Applicative[F]].pure(resultsPage("", None, Map.empty))
    }
  }

  private def buildResults(pageNumber: Option[Int], itemsPerPage: Option[Int], q: String): F[(PagedResults[CompanySearchResult], Map[CompaniesHouseId, Int])] = {
    searchResults(pageNumber, itemsPerPage, q).flatMap { results =>
      results.items.map(_.companiesHouseId).toList
        .traverse(id => countReports(id).map(id -> _))
        .map(counts => (results, Map(counts: _*)))
    }
  }

  private[controllers] def searchResults(pageNumber: Option[Int], itemsPerPage: Option[Int], q: String): F[PagedResults[CompanySearchResult]] =
    companySearch.searchCompanies(q, pageNumber.getOrElse(1), itemsPerPage.getOrElse(25))

  private[controllers] def countReports(companiesHouseId: CompaniesHouseId): F[Int] =
    evalDb(reportRepo.countFiledReports(companiesHouseId))
}

class SearchHelperImpl @Inject()(companySearch: CompanySearchService[Future], reportRepo: ReportRepo[DBIO], evalDb: DBIO ~> Future)(implicit ec: ExecutionContext)
  extends SearchHelperGen[Future, DBIO](companySearch, reportRepo, evalDb)(catsStdInstancesForFuture)
