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

package services.mocks

import models.CompaniesHouseId
import org.joda.time.LocalDateTime
import services.{CompanyAuthService, OAuthToken}

import scala.concurrent.Future

class MockCompanyAuth extends CompanyAuthService[Future] {

  val emails: Map[CompaniesHouseId, String] = Map(
    CompaniesHouseId("000000001") -> "foo@bar.com",
    CompaniesHouseId("000000002") -> "bar@baz.com"
  )

  override def authoriseUrl(companiesHouseId: CompaniesHouseId) = controllers.routes.CoHoOAuthMockController.login(companiesHouseId).url

  override def authoriseParams(companiesHouseId: CompaniesHouseId) = Map()

  override def isInScope(companiesHouseId: CompaniesHouseId, oAuthToken: OAuthToken): Future[Boolean] = Future.successful(true)

  override def emailAddress(companiesHouseId: CompaniesHouseId, oAuthToken: OAuthToken): Future[Option[String]] =
    Future.successful(Some(emails.getOrElse(companiesHouseId, "test@barbaz.com")))


  override def targetScope(companiesHouseId: CompaniesHouseId): String = ""

  override def convertCode(code: String): Future[OAuthToken] = Future.successful(OAuthToken("accessToken", LocalDateTime.now().plusMinutes(60), "refreshToken"))

  override def refreshAccessToken(oAuthToken: OAuthToken): Future[OAuthToken] = Future.successful(OAuthToken("accessToken", LocalDateTime.now().plusMinutes(60), "refreshToken"))
}
