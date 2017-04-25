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

import cats.Functor
import cats.syntax.functor._
import models.CompaniesHouseId
import play.api.mvc.Result
import play.twirl.api.Html
import services.{CompanyDetail, CompanySearchService}

trait CompanyHelper[F[_]] {
  implicit val fF : Functor[F] = implicitly[Functor[F]]

  def companySearch: CompanySearchService[F]

  import play.api.mvc.Results._

  def withCompany(companiesHouseId: CompaniesHouseId, foundResult: Html => Result = Ok(_))(body: CompanyDetail => Html): F[Result] = {
    companySearch.find(companiesHouseId).map {
      case Some(co) => foundResult(body(co))
      case None => BadRequest(s"Unknown company id ${companiesHouseId.id}")
    }
  }
}
