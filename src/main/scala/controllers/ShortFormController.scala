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

import actions.{CompanyAuthAction, CompanyAuthRequest}
import config.{PageConfig, ServiceConfig}
import controllers.FormPageDefs._
import forms.report._
import models.CompaniesHouseId
import play.api.i18n.MessagesApi
import play.api.mvc.{Controller, Result}
import play.twirl.api.Html
import services._

import scala.concurrent.{ExecutionContext, Future}

object ShortFormController {
  val shortFormId = "short-report-form"

  def publishTitle(companyName: String) = s"Publish a report for $companyName"
}

class ShortFormController @Inject()(
  validations: Validations,
  companyAuthAction: CompanyAuthAction,
  val serviceConfig: ServiceConfig,
  val pageConfig: PageConfig,
  val sessionService: SessionService,
  shortFormPageModel: ShortFormPageModel
)(implicit val ec: ExecutionContext, messages: MessagesApi)
  extends Controller
    with PageHelper
    with FormSessionHelpers {

  import ShortFormController._
  import shortFormPageModel._
  import validations._
  import views.html.{report => pages}

  private def reportPageHeader(implicit request: CompanyAuthRequest[_]): Html = h1(s"Publish a report for:<br>${request.companyDetail.companyName}")

  implicit def sessionIdFromRequest(implicit request: CompanyAuthRequest[_]): SessionId = request.sessionId

  //noinspection TypeAnnotation
  def show(companiesHouseId: CompaniesHouseId, change: Option[Boolean]) = companyAuthAction(companiesHouseId).async { implicit request =>
    val back =
      if (change.contains(true)) backCrumb(routes.ShortFormReviewController.showReview(companiesHouseId).url)
      else backCrumb(routes.ReportingPeriodController.show(companiesHouseId, change).url)
    val title = publishTitle(request.companyDetail.companyName)

    checkValidFromSession(emptyReportingPeriod, ShortFormName.ReportingPeriod.entryName).flatMap {
      case false => Future.successful(Redirect(routes.ReportingPeriodController.show(companiesHouseId, change)))
      case true  => loadFormData(emptyShortForm, ShortFormName.ShortForm).map { form =>
        Ok(page(title)(back, pages.shortForm(reportPageHeader, form, companiesHouseId, df, serviceStartDate, change)))
      }
    }
  }

  //noinspection TypeAnnotation
  def post(companiesHouseId: CompaniesHouseId, change: Option[Boolean]) = companyAuthAction(companiesHouseId).async(parse.urlFormEncoded) { implicit request =>
    val handler = handlerFor(ShortFormName.ShortForm)

    for {
      _ <- saveFormData(handler.formName, handler.bind.form)
      result <- handlePostFormPage(handler.formName, request.companyDetail, change.contains(true))
    } yield result
  }

  //noinspection TypeAnnotation
  def back(companiesHouseId: CompaniesHouseId, change: Option[Boolean]) = companyAuthAction(companiesHouseId) { implicit request =>
    if (change.contains(true)) Redirect(routes.ShortFormReviewController.showReview(companiesHouseId))
    else previousFormHandler(handlerFor(ShortFormName.ShortForm)) match {
      case Some(previousHandler) => Redirect(previousHandler.callPage(companiesHouseId, change = false))
      case None                  => Redirect(routes.ReportingPeriodController.show(companiesHouseId, None))
    }
  }

  private def handlePostFormPage(formName: ShortFormName, companyDetail: CompanyDetail, change: Boolean)(implicit request: CompanyAuthRequest[Map[String, Seq[String]]]): Future[Result] = {
    val title = publishTitle(companyDetail.companyName)

    bindUpToPage(formHandlers, formName).map {
      case FormHasErrors(handler)   => BadRequest(page(title)(handler.renderPage(reportPageHeader, companyDetail.companiesHouseId, change)))
      case FormIsOk(handler, value) => nextFormHandler(handler) match {
        case Some(nextHandler) => Redirect(nextHandler.callPage(companyDetail.companiesHouseId, change))
        case None              => Redirect(routes.ShortFormReviewController.showReview(companyDetail.companiesHouseId))
      }
      case FormIsBlank(handler)     => Ok(page(title)(handler.renderPage(reportPageHeader, request.companyDetail.companiesHouseId, change)))
    }
  }
}
