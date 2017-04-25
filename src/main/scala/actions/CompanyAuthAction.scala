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

package actions

import javax.inject.Inject

import cats.data.OptionT
import cats.instances.future._
import config.PageConfig
import controllers.PageHelper
import models.CompaniesHouseId
import org.joda.time.LocalDateTime
import org.scalactic.TripleEquals._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import repos.{SessionId, SessionRepo}
import services._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object CompanyAuthAction {
  val oAuthTokenKey = "oAuthToken"
  val companyDetailsKey = "companyDetails"
  val emailAddressKey = "emailAddress"
}

case class SessionDetails(companyDetails: CompanyDetail, emailAddress: String, oAuthToken: OAuthToken)

object SessionDetails {
  implicit val fmt = Json.format[SessionDetails]
}

case class CompanyAuthRequest[A](sessionId: SessionId, companyDetail: CompanyDetail, emailAddress: String, oAuthToken: OAuthToken, request: Request[A]) extends WrappedRequest[A](request)

/**
  * The `CompanyAuthAction` checks for the presence of a `sessionId` that corresponds to an entry in the
  * `session` table in the database. This session entry must contain the necessary details about the
  * company, the email address of the user who has authorised for filing, and the oAuth token details
  * needed to call the Companies House api. All of these are exposed as properties on the `CompanyAuthRequest`
  * so that action handlers can access them easily.
  *
  * If the access token retrieved from the session has expired then this action will refresh it using
  * the oAuth2 refresh token and update the session with the new values.
  */
class CompanyAuthAction @Inject()(
                                   SessionAction: SessionAction,
                                   sessionService: SessionRepo[Future],
                                   companyAuthService: CompanyAuthService[Future],
                                   val pageConfig: PageConfig)
                                 (implicit ec: ExecutionContext) extends PageHelper {
  def extractTime(s: String): Option[LocalDateTime] = Try(new LocalDateTime(s.toLong)).toOption

  def apply(expectedId: CompaniesHouseId): ActionBuilder[CompanyAuthRequest] = new ActionBuilder[CompanyAuthRequest] {
    override def invokeBlock[A](request: Request[A], block: (CompanyAuthRequest[A]) => Future[Result]) =
      (SessionAction andThen refiner(expectedId)).invokeBlock(request, block)
  }

  def forbidden(message: String)(implicit request: RequestHeader) = {
    Logger.info(s"User not authorised to access page: $message")
    Forbidden(page("Not authorised")(home, views.html.errors.forbidden403(message)))
  }

  def refiner(expectedId: CompaniesHouseId): ActionRefiner[SessionRequest, CompanyAuthRequest] = new ActionRefiner[SessionRequest, CompanyAuthRequest] {

    override protected def refine[A](request: SessionRequest[A]): Future[Either[Result, CompanyAuthRequest[A]]] = {
      implicit val rh: RequestHeader = request
      val sessionDetails = for {
        sessionDetails <- OptionT(sessionService.get[SessionDetails](request.sessionId))
        freshToken <- OptionT.liftF(freshenToken(request.sessionId, sessionDetails.oAuthToken))
      } yield CompanyAuthRequest(request.sessionId, sessionDetails.companyDetails, sessionDetails.emailAddress, freshToken, request.request)

      sessionDetails.value.map {
        case Some(car) if car.companyDetail.companiesHouseId === expectedId => Right(car)
        case Some(car) => Left(forbidden("company id from session does not match id in url"))
        case None => Left(forbidden("no company details found on request"))
      }
    }
  }

  def freshenToken(sessionId: SessionId, oAuthToken: OAuthToken): Future[OAuthToken] =
    if (oAuthToken.isExpired) {
      for {
        freshToken <- companyAuthService.refreshAccessToken(oAuthToken)
        _ <- sessionService.put(sessionId, CompanyAuthAction.oAuthTokenKey, freshToken)
      } yield freshToken
    } else Future.successful(oAuthToken)

}

