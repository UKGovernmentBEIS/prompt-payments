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
import cats.~>
import config.PageConfig
import play.api.i18n.MessagesApi
import repos.ReportRepo
import services.{CompanyAuthService, CompanySearchService}
import slick.dbio.DBIO
import slicks.repos.DBIOMonad._

import scala.concurrent.{ExecutionContext, Future}

class CoHoCodeControllerImpl @Inject()(companyAuth: CompanyAuthService[Future],
                                       companySearch: CompanySearchService[Future],
                                       reportRepo: ReportRepo[DBIO],
                                       pageConfig: PageConfig,
                                       evalDb: DBIO ~> Future,
                                       evalF: Future ~> Future
                                      )(implicit ec: ExecutionContext, messagesApi: MessagesApi)
  extends CoHoCodeController[Future, DBIO](companyAuth, companySearch, reportRepo, pageConfig, evalDb, evalF)



