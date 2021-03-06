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
import models.{CompaniesHouseId, ReportId}
import play.api.data.Form
import play.api.data.Forms.{single, _}
import play.api.i18n.MessagesApi
import play.api.mvc.{Call, Controller, Result}
import play.twirl.api.Html
import services._
import views.html.helpers.ReviewPageData

import scala.concurrent.{ExecutionContext, Future}

object ReviewPage {
  val reviewTableId = "review-table"
}

class ShortFormReviewController @Inject()(
  reports: ReportService,
  validations: Validations,
  val companyAuth: CompanyAuthService,
  companyAuthAction: CompanyAuthAction,
  val serviceConfig: ServiceConfig,
  val pageConfig: PageConfig,
  val sessionService: SessionService,
  shortFormPageModel: ShortFormPageModel,
  reviewPageData: ReviewPageData,
  eventHandler: EventHandler
)(implicit val ec: ExecutionContext, messages: MessagesApi)
  extends Controller
    with BaseFormController
    with PageHelper
    with FormSessionHelpers
    with FormControllerHelpers[ShortFormModel, ShortFormName] {

  import validations._
  import views.html.{report => pages}

  private val reviewPageTitle = "Review your report"

  private def publishTitle(companyName: String) = s"Publish a report for $companyName"

  implicit def companyDetail(implicit request: CompanyAuthRequest[_]): CompanyDetail = request.companyDetail

  def reportPageHeader(implicit request: CompanyAuthRequest[_]): Html = h1(s"Publish a report for:<br>${request.companyDetail.companyName}")

  override def formHandlers: Seq[ShortFormHandler[_]] =
    shortFormPageModel.formHandlers

  override def bindMainForm(implicit sessionId: SessionId): Future[Option[ShortFormModel]] =
    loadFormData(emptyShortForm, ShortFormName.ShortForm).map(_.value)

  override def bindReportingPeriod(implicit sessionId: SessionId): Future[Option[ReportingPeriodFormModel]] =
    loadFormData(emptyReportingPeriod, ShortFormName.ReportingPeriod).map(_.value)

  override def emptyReportingPeriod: Form[ReportingPeriodFormModel] =
    validations.emptyReportingPeriod

  //noinspection TypeAnnotation
  def showReview(companiesHouseId: CompaniesHouseId) = companyAuthAction(companiesHouseId).async { implicit request =>
    handleBinding(request, renderReview)
  }

  //noinspection TypeAnnotation
  def postReview(companiesHouseId: CompaniesHouseId) = companyAuthAction(companiesHouseId).async(parse.urlFormEncoded) { implicit request =>
    val f: (CompanyAuthRequest[Map[String, Seq[String]]], ReportingPeriodFormModel, ShortFormModel) => Future[Result] = handleReviewPost
    val revise: Boolean = Form(single("revise" -> text)).bindForm.value.contains("Revise")

    if (revise) Future.successful(Redirect(routes.ReportingPeriodController.show(companiesHouseId, None)))
    else handleBinding(request, f)
  }

  private def renderReview(request: CompanyAuthRequest[_], reportingPeriod: ReportingPeriodFormModel, shortForm: ShortFormModel): Future[Result] = {
    implicit val req: CompanyAuthRequest[_] = request
    val backLink = backCrumb(routes.ShortFormController.show(request.companyDetail.companiesHouseId, None).url)

    val action: Call = routes.ShortFormReviewController.postReview(request.companyDetail.companiesHouseId)
    val formGroups = reviewPageData.formGroups(reportingPeriod, shortForm)
    Future.successful(Ok(page(reviewPageTitle)(backLink, pages.review(emptyReview, formGroups, action))))
  }

  private def handleReviewPost(request: CompanyAuthRequest[Map[String, Seq[String]]], reportingPeriod: ReportingPeriodFormModel, shortForm: ShortFormModel): Future[Result] = {
    implicit val req: CompanyAuthRequest[Map[String, Seq[String]]] = request

    val action: Call = routes.ShortFormReviewController.postReview(request.companyDetail.companiesHouseId)
    val formGroups = reviewPageData.formGroups(reportingPeriod, shortForm)
    emptyReview.bindForm.fold(
      errs => Future.successful(BadRequest(page(reviewPageTitle)(home, pages.review(errs, formGroups, action)))),
      review => {
        if (review.confirmed) verifyingOAuthScope(request.companyDetail.companiesHouseId, request.oAuthToken) {
          for {
            reportId <- createReport(request.companyDetail, request.emailAddress, reportingPeriod, shortForm, review.confirmedBy, er.report)
            _ <- clearFormData
          } yield Redirect(controllers.routes.ConfirmationController.showConfirmation(reportId))
        } else {
          Future.successful(BadRequest(page(reviewPageTitle)(home, pages.review(emptyReview.fill(review), formGroups, action))))
        }
      }
    )
  }

  private[controllers] def createReport(companyDetail: CompanyDetail, emailAddress: String, reportingPeriod: ReportingPeriodFormModel, shortForm: ShortFormModel, confirmedBy: String, urlFunction: ReportId => String): Future[ReportId] = {
    reports.createShortReport(companyDetail, reportingPeriod, shortForm, confirmedBy, emailAddress, urlFunction).map { reportId =>
      eventHandler.reportPublished(companyDetail, urlFunction(reportId))
      reportId
    }
  }
}
