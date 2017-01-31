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

package forms

import org.joda.time.LocalDate
import play.api.data.Forms._
import play.api.data.Mapping

import scala.util.Try

case class CompaniesHouseId(id: String)

object CompaniesHouseId {
  val companiesHouseId: Mapping[CompaniesHouseId] = nonEmptyText.transform(s => CompaniesHouseId(s), (c: CompaniesHouseId) => c.id)
}

case class PaymentHistory(
                           averageTimeToPay: Int,
                           percentPaidWithinAgreedTerms: BigDecimal,
                           percentWithin30Days: BigDecimal,
                           percentWithin60Days: BigDecimal,
                           percentBeyond60Days: BigDecimal
                         )

object PaymentHistory {
  val paymentHistory: Mapping[PaymentHistory] = mapping(
    "averageTimeToPay" -> number,
    "percentPaidWithinAgreedTerms" -> bigDecimal,
    "percentWithin30Days" -> bigDecimal,
    "percentWithin60Days" -> bigDecimal,
    "percentBeyond60Days" -> bigDecimal
  )(PaymentHistory.apply)(PaymentHistory.unapply)
}

case class PaymentTerms(
                         terms: String,
                         maximumContractPeriod: String,
                         paymentTermsChanged: Boolean,
                         paymentTermsChangedComment: Option[String],
                         paymentTermsChangedNotified: Boolean,
                         paymentTermsChangedNotifiedComment: Option[String],
                         paymentTermsComment: Option[String]
                       )

object PaymentTerms {
  val paymentTerms: Mapping[PaymentTerms] = mapping(
    "terms" -> nonEmptyText,
    "maximumContractPeriod" -> nonEmptyText,
    "paymentTermsChanged" -> boolean,
    "paymentTermsChangedComment" -> optional(nonEmptyText),
    "paymentTermsNotified" -> boolean,
    "paymentTermsNotifiedComment" -> optional(nonEmptyText),
    "paymentTermsComment" -> optional(nonEmptyText)
  )(PaymentTerms.apply)(PaymentTerms.unapply)
    .verifying("error.changedcomment.required", pt => pt.paymentTermsChanged && pt.paymentTermsChangedComment.isDefined)
    .verifying("error.notifiedcomment.required", pt => pt.paymentTermsChanged && pt.paymentTermsChangedNotified && pt.paymentTermsChangedNotifiedComment.isDefined)

  def validateTermsChanged(paymentTerms: PaymentTerms): Boolean = {
    (paymentTerms.paymentTermsChanged, paymentTerms.paymentTermsChangedComment) match {
      case (true, Some(_)) => true
      case (false, None) => true
      case _ => false
    }
  }
}

case class DateFields(day: Int, month: Int, year: Int)

/**
  * DateFields provides two mappings. On will check that that the three day/month/year fields
  * are numbers and the other will build on that to transform valid combinations of those three
  * individual values to a Joda LocalDate.
  */
object DateFields {
  val dateFields: Mapping[DateFields] = mapping(
    "day" -> number,
    "month" -> number,
    "year" -> number
  )(DateFields.apply)(DateFields.unapply)
    .verifying("error.date", fields => validateFields(fields))

  val dateFromFields: Mapping[LocalDate] = dateFields.transform(toDate, fromDate)

  private def validateFields(fields: DateFields): Boolean = Try(toDate(fields)).isSuccess

  /**
    * Warning: Will throw an exception if the fields don't constitute a valid date. This is provided
    * to support the `.transform` call below on the basis that the fields themselves will have alreadybeen
    * been verified with `validateFields`
    */
  private def toDate(fields: DateFields): LocalDate = new LocalDate(fields.year, fields.month, fields.day)

  private def fromDate(date: LocalDate): DateFields = DateFields(date.getDayOfMonth, date.getMonthOfYear, date.getYear)
}

case class ReportFormModel(
                            companiesHouseId: CompaniesHouseId,
                            startDate: LocalDate,
                            endDate: LocalDate,
                            paymentHistory: PaymentHistory,
                            paymentTerms: PaymentTerms,
                            disputeResolution: String,
                            hasPaymentCodes: Boolean,
                            paymentCodes: Option[String],
                            offerEInvoicing: Boolean,
                            offerSupplyChainFinancing: Boolean,
                            retentionChargesInPolicy: Boolean,
                            retentionChargesInPast: Boolean
                          )

object ReportFormModel {

  import CompaniesHouseId.companiesHouseId
  import DateFields.dateFromFields
  import PaymentHistory.paymentHistory
  import PaymentTerms.paymentTerms

  val reportFormModel = mapping(
    "companiesHouseId" -> companiesHouseId,
    "startDate" -> dateFromFields,
    "endDate" -> dateFromFields,
    "paymentHistory" -> paymentHistory,
    "paymentTerms" -> paymentTerms,
    "disputeResolution" -> nonEmptyText,
    "hasPaymentCodes" -> boolean,
    "paymentCodes" -> optional(nonEmptyText),
    "offerEInvoicing" -> boolean,
    "offerSupplyChainFinancing" -> boolean,
    "retentionChargesInPolicy" -> boolean,
    "retentionChargesInPast" -> boolean
  )(ReportFormModel.apply)(ReportFormModel.unapply)
    .verifying("error.paymentcodes.required", rf => rf.hasPaymentCodes && rf.paymentCodes.isDefined)
}
