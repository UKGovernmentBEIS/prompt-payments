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

import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.{Monad, ~>}
import models.CompaniesHouseId
import play.twirl.api.Html
import repos.ReportRepo
import services.{CompanySearchResult, CompanySearchService, PagedResults}

trait SearchHelper[F[_], DbEffect[_]] {
  implicit val monadF: Monad[F]
  implicit val evalDb: DbEffect ~> F

  def companySearch: CompanySearchService[F]

  def reportRepo: ReportRepo[DbEffect]

  type ResultsPageFunction = (String, Option[PagedResults[CompanySearchResult]], Map[CompaniesHouseId, Int]) => Html

  def doSearch(query: Option[String], pageNumber: Option[Int], itemsPerPage: Option[Int], resultsPage: ResultsPageFunction): F[Html] = {
    query match {
      case Some(q) => companySearch.searchCompanies(q, pageNumber.getOrElse(1), itemsPerPage.getOrElse(25)).flatMap { results =>
        results.items.toList.traverse { result =>
          evalDb(reportRepo.byCompanyNumber(result.companiesHouseId)).map(rs => (result.companiesHouseId, rs.count(_.isFiled)))
        }.map(counts => resultsPage(q, Some(results), Map(counts: _*)))
      }

      case None => monadF.pure(resultsPage("", None, Map.empty))
    }
  }
}
