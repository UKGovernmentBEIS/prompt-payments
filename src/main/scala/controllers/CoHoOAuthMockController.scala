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

import actions.CompanyAuthAction.{companyDetailsKey, emailAddressKey, oAuthTokenKey}
import actions.SessionAction
import cats.data.OptionT
import cats.instances.future._
import config.PageConfig
import models.CompaniesHouseId
import org.joda.time.LocalDateTime
import play.api.mvc.{Action, Controller}
import repos.SessionRepo
import services.{CompanyAuthService, CompanySearchService, OAuthToken}

import scala.concurrent.{ExecutionContext, Future}

class CoHoOAuthMockController @Inject()(
                                         companySearch: CompanySearchService[Future],
                                         companyAuth: CompanyAuthService[Future],
                                         sessionService: SessionRepo[Future],
                                         SessionAction: SessionAction,
                                         val pageConfig: PageConfig)
                                       (implicit ec: ExecutionContext) extends Controller with PageHelper {

  def login(companiesHouseId: CompaniesHouseId) = Action {
    Ok(views.html.oauthMock.mockCohoLogin(companiesHouseId))
  }

  def postLogin(companiesHouseId: CompaniesHouseId) = Action { request => Redirect(controllers.routes.CoHoOAuthMockController.authCode(companiesHouseId)) }

  def authCode(companiesHouseId: CompaniesHouseId) = Action.async { implicit request =>
    companySearch.find(companiesHouseId).map {
      case Some(co) =>
        Ok(views.html.oauthMock.mockCohoAuthCode(companiesHouseId, co.companyName))
      case None => BadRequest(s"Unknown company id ${companiesHouseId.id}")
    }
  }

  def postAuthCode(companiesHouseId: CompaniesHouseId) = SessionAction.async { implicit request =>
    val ref = OAuthToken("accessToken", LocalDateTime.now().plusMinutes(60), "refreshToken")

    val f = for {
      companyDetail <- OptionT(companySearch.find(companiesHouseId))
      emailAddress <- OptionT(companyAuth.emailAddress(companiesHouseId, ref))
      _ <- OptionT.liftF(sessionService.put(request.sessionId, oAuthTokenKey, ref))
      _ <- OptionT.liftF(sessionService.put(request.sessionId, companyDetailsKey, companyDetail))
      _ <- OptionT.liftF(sessionService.put(request.sessionId, emailAddressKey, emailAddress))
    } yield Redirect(controllers.routes.FilingController.file(companiesHouseId))

    f.value.map {
      case Some(result) => result
      case None => BadRequest(s"Unable to find company details for state $companiesHouseId")
    }
  }

}
