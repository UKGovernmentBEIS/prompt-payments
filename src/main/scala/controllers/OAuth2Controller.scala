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

import actions.SessionAction
import cats.data.OptionT
import cats.instances.future._
import models.CompaniesHouseId
import play.api.mvc._
import repos.SessionRepo
import services._

import scala.concurrent.{ExecutionContext, Future}

class OAuth2Controller @Inject()(
                                  sessionService: SessionRepo[Future],
                                  companySearchService: CompanySearchService[Future],
                                  companyAuthService: CompanyAuthService[Future],
                                  SessionAction: SessionAction)(implicit exec: ExecutionContext) extends Controller {

  def startOauthDance(companiesHouseId: CompaniesHouseId)(implicit request: RequestHeader): Result = {
    Redirect(companyAuthService.authoriseUrl(companiesHouseId), companyAuthService.authoriseParams(companiesHouseId))
  }

  def claimCallback(code: Option[String], state: Option[String], error: Option[String], errorDescription: Option[String], errorCode: Option[String]) =
    SessionAction.async { implicit request =>
      val tokenDetails: Future[Either[Result, OAuthToken]] = code match {
        case None => Future.successful(Left(BadRequest("No oAuth code provided")))
        case Some(c) => companyAuthService.convertCode(c).map(Right(_))
      }

      import actions.CompanyAuthAction._

      tokenDetails.flatMap {
        case Left(result) => Future.successful(result)
        case Right(ref) => {
          for {
            companyId <- OptionT.fromOption(state.map(CompaniesHouseId))
            companyDetail <- OptionT(companySearchService.find(companyId))
            emailAddress <- OptionT(companyAuthService.emailAddress(companyId, ref))
            _ <- OptionT.liftF(sessionService.put(request.sessionId, oAuthTokenKey, ref))
            _ <- OptionT.liftF(sessionService.put(request.sessionId, companyDetailsKey, companyDetail))
            _ <- OptionT.liftF(sessionService.put(request.sessionId, emailAddressKey, emailAddress))
          } yield Redirect(controllers.routes.FilingController.file(companyId))
        }.value.map {
          case Some(result) => result
          case None => BadRequest(s"Unable to find company details for state $state")
        }
      }
    }
}

