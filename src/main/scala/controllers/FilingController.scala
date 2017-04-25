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

import javax.inject.{Inject, Named}

import actions.{CompanyAuthAction, CompanyAuthRequest}
import akka.actor.ActorRef
import config.{PageConfig, ServiceConfig}
import forms.report.{ReportFormModel, ReportReviewModel, Validations}
import models.{CompaniesHouseId, ReportId}
import org.joda.time.format.DateTimeFormat
import play.api.data.Form
import play.api.data.Forms.{single, _}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, Controller, Result}
import play.twirl.api.Html
import repos.ReportRepo
import services._

import scala.concurrent.{ExecutionContext, Future}

class FilingController @Inject()(
                                  notifyService: NotifyService[Future],
                                  reports: ReportRepo[Future],
                                  reportValidations: Validations,
                                  companyAuth: CompanyAuthService[Future],
                                  CompanyAuthAction: CompanyAuthAction,
                                  serviceConfig: ServiceConfig,
                                  val pageConfig: PageConfig,
                                  @Named("confirmation-actor") confirmationActor: ActorRef
                                )(implicit ec: ExecutionContext, messages: MessagesApi) extends Controller with PageHelper {

  import views.html.{report => pages}

  val emptyReport: Form[ReportFormModel] = Form(reportValidations.reportFormModel)
  val emptyReview: Form[ReportReviewModel] = Form(reportValidations.reportReviewModel)
  private val reviewPageTitle = "Review your report"
  val df = DateTimeFormat.forPattern("d MMMM YYYY")
  val serviceStartDate = serviceConfig.startDate.getOrElse(ServiceConfig.defaultServiceStartDate)

  def reportPageHeader(implicit request: CompanyAuthRequest[_]): Html = h1(s"Publish a report for:<br>${request.companyDetail.companyName}")

  private def publishTitle(companyName: String) = s"Publish a report for $companyName"

  def file(companiesHouseId: CompaniesHouseId) = CompanyAuthAction(companiesHouseId) { implicit request =>
    Ok(page(publishTitle(request.companyDetail.companyName))(home, pages.file(reportPageHeader, emptyReport, companiesHouseId, df, serviceStartDate)))
  }

  def postForm(companiesHouseId: CompaniesHouseId) = CompanyAuthAction(companiesHouseId)(parse.urlFormEncoded) { implicit request =>
    emptyReport.bindFromRequest().fold(
      errs => BadRequest(page(publishTitle(request.companyDetail.companyName))(home, pages.file(reportPageHeader, errs, companiesHouseId, df, serviceStartDate))),
      report => Ok(page(reviewPageTitle)(home, pages.review(emptyReview, report, companiesHouseId, request.companyDetail.companyName, df, reportValidations.reportFormModel)))
    )
  }

  def postReview(companiesHouseId: CompaniesHouseId) = CompanyAuthAction(companiesHouseId).async(parse.urlFormEncoded) { implicit request =>
    val revise = Form(single("revise" -> text)).bindFromRequest().value.contains("Revise")

    // Re-capture the values for the report itself. In theory these values should always be valid
    // (as we only send the user to the review page if they are) but if somehow they aren't then
    // send the user back to the report form to fix them.
    emptyReport.bindFromRequest().fold(
      errs => Future.successful(BadRequest(page(publishTitle(request.companyDetail.companyName))(home, pages.file(reportPageHeader, errs, companiesHouseId, df, serviceStartDate)))),
      report =>
        if (revise) Future.successful(Ok(page(publishTitle(request.companyDetail.companyName))(home, pages.file(reportPageHeader, emptyReport.fill(report), companiesHouseId, df, serviceStartDate))))
        else checkConfirmation(companiesHouseId, report)
    )
  }

  private def checkConfirmation(companiesHouseId: CompaniesHouseId, report: ReportFormModel)(implicit request: CompanyAuthRequest[_]): Future[Result] = {
    emptyReview.bindFromRequest().fold(
      errs => Future.successful(BadRequest(page(reviewPageTitle)(home, pages.review(errs, report, companiesHouseId, request.companyDetail.companyName, df, reportValidations.reportFormModel)))),
      review => {
        if (review.confirmed) verifyingOAuthScope(companiesHouseId, request.oAuthToken) {
          createReport(companiesHouseId, report, review).map(rId => Redirect(controllers.routes.FilingController.showConfirmation(rId)))
        }
        else
          Future.successful(BadRequest(page(reviewPageTitle)(home, pages.review(emptyReview.fill(review), report, companiesHouseId, request.companyDetail.companyName, df, reportValidations.reportFormModel))))
      }
    )
  }

  private def verifyingOAuthScope(companiesHouseId: CompaniesHouseId, oAuthToken: OAuthToken)(body: => Future[Result]): Future[Result] = {
    companyAuth.isInScope(companiesHouseId, oAuthToken).flatMap {
      case true => body
      case false => Future.successful(Redirect(controllers.routes.FilingController.invalidScope(companiesHouseId)))
    }
  }

  private def createReport(companiesHouseId: CompaniesHouseId, report: ReportFormModel, review: ReportReviewModel)(implicit request: CompanyAuthRequest[_]): Future[ReportId] = {

    val urlFunction: ReportId => String = (id: ReportId) => controllers.routes.ReportController.view(id).absoluteURL()
    for {
      reportId <- reports.create(review.confirmedBy, companiesHouseId, request.companyDetail.companyName, report, review, request.emailAddress, urlFunction)
      _ <- Future.successful(confirmationActor ! 'poll)
    } yield reportId
  }

  def showConfirmation(reportId: ReportId) = Action.async { implicit request =>
    reports.findFiled(reportId).map {
      case Some(report) => Ok(
        page(s"You have published a report for ${report.header.companyName}")
        (home, pages.filingSuccess(reportId, report.filing.confirmationEmailAddress, pageConfig.surveyMonkeyConfig)))
      case None => BadRequest(s"Could not find a report with id ${reportId.id}")
    }
  }

  def invalidScope(companiesHouseId: CompaniesHouseId) = CompanyAuthAction(companiesHouseId) { implicit request =>
    Ok(page("Your report has not been filed because of an error")(home, pages.invalidScope(request.companyDetail)))
  }
}
