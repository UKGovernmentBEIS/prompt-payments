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

import cats.{Monad, MonadError, ~>}
import config.PageConfig
import models.CompaniesHouseId
import play.api.data.Forms.single
import play.api.data.{Form, FormError, Forms}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, Controller, RequestHeader, Result}
import play.twirl.api.Html
import repos.ReportRepo
import services.{CompanyAuthService, CompanySearchService}
import utils.AdjustErrors

import scala.concurrent.Future

class CoHoCodeController[F[_], DbEffect[_]](companyAuth: CompanyAuthService[F],
                                            val companySearch: CompanySearchService[F],
                                            val reportRepo: ReportRepo[DbEffect],
                                            val pageConfig: PageConfig,
                                            val evalDb: DbEffect ~> F,
                                            evalF: F ~> Future
                                           )(implicit val monadF: MonadError[F, Throwable],
                                             dbEffectMonad: Monad[DbEffect],
                                             messages: MessagesApi)
  extends Controller
    with PageHelper
    with SearchHelper[F, DbEffect]
    with CompanyHelper[F] {

  import CoHoCodeController._
  import views.html.{report => pages}

  private def codePage(companiesHouseId: CompaniesHouseId, form: Form[CodeOption] = emptyForm, foundResult: Html => Result = Ok(_))
                      (implicit messages: MessagesApi, rh: RequestHeader) =
    withCompany(companiesHouseId, foundResult) { co =>
      page("If you don't have a Companies House authentication code")(home, pages.companiesHouseOptions(co.companyName, companiesHouseId, form))
    }

  def code(companiesHouseId: CompaniesHouseId) = Action.async(implicit request => evalF(codePage(companiesHouseId)))


  def codeOptions(companiesHouseId: CompaniesHouseId) = Action.async { implicit request =>
    evalF {
      emptyForm.bindFromRequest().fold(errs => codePage(companiesHouseId, errs, BadRequest(_)), c => implicitly[Monad[F]].pure(resultFor(c, companiesHouseId)))
    }
  }
}

object CoHoCodeController {

  import enumeratum.EnumEntry.Lowercase
  import enumeratum._
  import play.api.mvc.Results.Redirect
  import utils.EnumFormatter

  sealed trait CodeOption extends EnumEntry with Lowercase

  object CodeOption extends Enum[CodeOption] with EnumFormatter[CodeOption] {
    override def values = findValues

    case object Colleague extends CodeOption

    case object Register extends CodeOption

  }

  /**
    * Override any error from the EnumFormatter with a simple "need to choose
    * an option to continue" error.
    */
  val codeOptionMapping = AdjustErrors(Forms.of[CodeOption]) { (k, errs) =>
    if (errs.isEmpty) errs else Seq(FormError(k, "error.needchoicetocontinue"))
  }

  val emptyForm: Form[CodeOption] = Form(single("nextstep" -> codeOptionMapping))

  def resultFor(codeOption: CodeOption, companiesHouseId: CompaniesHouseId): Result = {
    codeOption match {
      case CodeOption.Colleague => Redirect(routes.ReportController.colleague(companiesHouseId))
      case CodeOption.Register => Redirect(routes.ReportController.register(companiesHouseId))
    }
  }

}