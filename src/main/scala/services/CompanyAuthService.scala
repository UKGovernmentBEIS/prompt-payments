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

package services

import models.CompaniesHouseId

import scala.concurrent.Future

case class OAuthError(error_description: String, error: String)

sealed trait CodeConversionError
case object CodeAlreadySeen extends CodeConversionError
case class ErrorInConversion(oAuthError: OAuthError) extends CodeConversionError


trait CompanyAuthService {
  def authoriseUrl(companiesHouseId: CompaniesHouseId): String

  def convertCode(code: String): Future[Either[CodeConversionError, OAuthToken]]

  def refreshAccessToken(oAuthToken: OAuthToken): Future[OAuthToken]

  def authoriseParams(companiesHouseId: CompaniesHouseId): Map[String, Seq[String]]

  def isInScope(companiesHouseId: CompaniesHouseId, oAuthToken: OAuthToken): Future[Boolean]

  def emailAddress(companiesHouseId: CompaniesHouseId, oAuthToken: OAuthToken): Future[Option[String]]

  def targetScope(companiesHouseId: CompaniesHouseId): String
}

