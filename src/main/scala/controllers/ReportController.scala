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
import cats.syntax.functor._
import cats.{Monad, MonadError, ~>}
import config.PageConfig
import forms.Validations
import models.{CompaniesHouseId, ReportId}
import org.joda.time.format.DateTimeFormat
import org.scalactic.TripleEquals._
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, Controller}
import repos.ReportRepo
import services._
import slick.dbio.DBIO
import slicks.repos.DBIOMonad._
import utils.YesNo

import scala.concurrent.{ExecutionContext, Future}

class ReportController @Inject()(
                                  companyAuth: CompanyAuthService[Future],
                                  searchHelper: SearchHelper[Future],
                                  companySearch: CompanySearchService[Future],
                                  reportRepo: ReportRepo[DBIO],
                                  pageConfig: PageConfig,
                                  evalDb: DBIO ~> Future,
                                  evalF: Future ~> Future
                                )(implicit ec: ExecutionContext, messages: MessagesApi)
  extends ReportControllerGen[Future, DBIO](companyAuth, searchHelper, companySearch, reportRepo, pageConfig, evalDb, evalF)

class ReportControllerGen[F[_], DbEffect[_]](
                                              companyAuth: CompanyAuthService[F],
                                              searchHelper: SearchHelper[F],
                                              val companySearch: CompanySearchService[F],
                                              reportRepo: ReportRepo[DbEffect],
                                              val pageConfig: PageConfig,
                                              val evalDb: DbEffect ~> F,
                                              evalF: F ~> Future
                                            )(implicit val monadF: MonadError[F, Throwable],
                                              dbEffectMonad: Monad[DbEffect], messages: MessagesApi)
  extends Controller
    with PageHelper
    with CompanyHelper[F] {

  import views.html.{report => pages}

  private val searchPageTitle = "Search for a company"
  private val signInPageTitle = "Sign in using your Companies House account"

  private def publishTitle(companyName: String) = s"Publish a report for $companyName"

  val searchHeader = h1("Publish a report")
  val searchLink = routes.ReportController.search(None, None, None).url
  val companyLink = { id: CompaniesHouseId => routes.ReportController.start(id).url }

  def pageLink(query: Option[String], itemsPerPage: Option[Int], pageNumber: Int) = routes.ReportController.search(query, Some(pageNumber), itemsPerPage).url

  def search(query: Option[String], pageNumber: Option[Int], itemsPerPage: Option[Int]) = Action.async { implicit request =>
    def resultsPage(q: String, results: Option[PagedResults[CompanySearchResult]], countMap: Map[CompaniesHouseId, Int]) =
      page(searchPageTitle)(home, searchHeader, views.html.search.search(q, results, countMap, searchLink, companyLink, pageLink(query, itemsPerPage, _)))

    evalF(searchHelper.doSearch(query, pageNumber, itemsPerPage, resultsPage).map(Ok(_)))
  }

  def start(companiesHouseId: CompaniesHouseId) = Action.async { implicit request =>
    evalF {
      companySearch.find(companiesHouseId).map {
        case Some(co) => Ok(page(publishTitle(co.companyName))(home, pages.start(co.companyName, co.companiesHouseId)))
        case None => NotFound(s"Could not find a company with id ${companiesHouseId.id}")
      }
    }
  }

  val hasAccountChoice = Form(single("account" -> Validations.yesNo))

  def preLogin(companiesHouseId: CompaniesHouseId) = Action { implicit request =>
    Ok(page(signInPageTitle)(home, pages.preLogin(companiesHouseId, hasAccountChoice))).removingFromSession(SessionAction.sessionIdKey)
  }

  def login(companiesHouseId: CompaniesHouseId) = Action { implicit request =>
    hasAccountChoice.bindFromRequest().fold(
      errs => BadRequest(page(signInPageTitle)(home, pages.preLogin(companiesHouseId, errs))),
      hasAccount =>
        if (hasAccount === YesNo.Yes) Redirect(companyAuth.authoriseUrl(companiesHouseId), companyAuth.authoriseParams(companiesHouseId))
        else Redirect(routes.CoHoCodeControllerImpl.code(companiesHouseId))
    )
  }

  def colleague(companiesHouseId: CompaniesHouseId) = Action.async { implicit request =>
    evalF(withCompany(companiesHouseId)(co => page("If you want a colleague to publish a report")(home, pages.askColleague(co.companyName, companiesHouseId))))
  }

  def register(companiesHouseId: CompaniesHouseId) = Action.async { implicit request =>
    evalF(withCompany(companiesHouseId)(co => page("Request an authentication code")(home, pages.requestAccessCode(co.companyName, companiesHouseId))))
  }

  def applyForAuthCode(companiesHouseId: CompaniesHouseId) = Action { implicit request =>
    Redirect(companyAuth.authoriseUrl(companiesHouseId), companyAuth.authoriseParams(companiesHouseId))
  }

  val df = DateTimeFormat.forPattern("d MMMM YYYY")

  def view(reportId: ReportId) = Action.async { implicit request =>
    val f = for {
      report <- OptionT(reportRepo.findFiled(reportId))
    } yield {
      val crumbs = breadcrumbs(homeBreadcrumb)
      Ok(page(s"Payment practice report for ${report.header.companyName}")(crumbs, views.html.search.report(report, df)))
    }

    evalF {
      evalDb(f.value).map {
        case Some(ok) => ok
        case None => NotFound
      }
    }
  }
}

