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

import actors.ConfirmationActor
import cats.arrow.FunctionK
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, TypeLiteral}
import config._
import controllers.{SearchService, SearchServiceImpl}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.{Configuration, Environment, Logger}
import repos.SessionCleaner
import services._
import services.live.{CompaniesHouseAuth, CompaniesHouseSearch, NotifyServiceImpl}
import services.mocks.{MockCompanyAuth, MockCompanySearch, MockNotify}
import slick.dbio.DBIO
import slicks.modules.DB
import slicks.repos.EvalDB

import scala.concurrent.Future

class Module(environment: Environment, configuration: Configuration) extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {

    val config = new AppConfig(configuration).config

    config.companiesHouse match {
      case Some(ch) =>
        bind(classOf[CompaniesHouseConfig]).toInstance(ch)
        bind(new TypeLiteral[CompanySearchService[Future]] {}).to(classOf[CompaniesHouseSearch])
      case None =>
        Logger.debug("Wiring in Company Search Mock")
        bind(new TypeLiteral[CompanySearchService[Future]] {}).to(classOf[MockCompanySearch])
    }

    config.oAuth match {
      case Some(o) =>
        bind(classOf[OAuthConfig]).toInstance(o)
        bind(new TypeLiteral[CompanyAuthService[Future]] {}).to(classOf[CompaniesHouseAuth])
      case None =>
        Logger.debug("Wiring in Company Auth Mock")
        bind(new TypeLiteral[CompanyAuthService[Future]] {}).to(classOf[MockCompanyAuth])
    }

    config.notifyService match {
      case Some(n) =>
        bind(classOf[NotifyConfig]).toInstance(n)
        bind(new TypeLiteral[NotifyService[Future]] {}).to(classOf[NotifyServiceImpl])

      case None =>
        Logger.debug("Wiring in Notify Mock")
        bind(new TypeLiteral[NotifyService[Future]] {}).to(classOf[MockNotify])
    }

    bind(new TypeLiteral[SearchService[Future]]{}).to(classOf[SearchServiceImpl])
    bind(new TypeLiteral[FunctionK[DBIO, Future]] {}).to(classOf[EvalDB])
    bind(new TypeLiteral[FunctionK[Future, Future]] {}).toInstance(FunctionK.id[Future])

    bind(classOf[Int])
      .annotatedWith(Names.named("session timeout"))
      .toInstance(config.sessionTimeoutInMinutes.getOrElse(60))

    bind(classOf[PageConfig]).toInstance(config.pageConfig)

    bind(classOf[ServiceConfig])
      .toInstance(config.service.getOrElse(ServiceConfig.empty))

    bind(classOf[DB]).asEagerSingleton()
    bindActor[ConfirmationActor]("confirmation-actor")

    bind(classOf[SessionCleaner]).asEagerSingleton()
  }
}
