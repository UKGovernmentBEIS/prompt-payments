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

package forms.report

import dbrows.{PaymentHistoryRow, PaymentTermsRow}
import forms.DateRange
import org.scalactic.TripleEquals._
import repos.FiledReport
import utils.YesNo
import utils.YesNo.{No, Yes}

object ReportConstants {
  /**
    * We need to translate word counts into character counts for the database. This constant
    * defines the average word length (including whitespace)
    */
  val averageWordLength = 7

  val longTerms = 5000
  val longComment = 2000
  val shortComment = 500

  val paymentTermsWordCount = longTerms
  val paymentTermsCharCount = paymentTermsWordCount * averageWordLength

  val maxContractPeriodCommentWordCount = shortComment
  val maxContractPeriodCommentCharCount = maxContractPeriodCommentWordCount * averageWordLength

  val paymentTermsCommentWordCount = shortComment
  val paymentTermsCommentCharCount = paymentTermsCommentWordCount * averageWordLength

  val disputeResolutionWordCount = longComment
  val disputeResolutionCharCount = disputeResolutionWordCount * averageWordLength

  val paymentTermsChangedWordCount = shortComment
  val paymentTermsChangedCharCount = paymentTermsChangedWordCount * averageWordLength

  val paymentTermsNotifiedWordCount = shortComment
  val paymentTermsNotifiedCharCount = paymentTermsNotifiedWordCount * averageWordLength

  val paymentCodesWordCount = 35
  val paymentCodesCharCount = paymentCodesWordCount * averageWordLength
}

case class ConditionalText(yesNo: YesNo, text: Option[String]) {
  def normalize = this match {
    case ConditionalText(No, _) => ConditionalText(No, None)
    case ConditionalText(Yes, Some(t)) if t.trim() === "" => ConditionalText(Yes, None)
    case _ => this
  }
}

object ConditionalText {
  def apply(o: Option[String]): ConditionalText =
    o.map(s => ConditionalText(Yes, Some(s))).getOrElse(ConditionalText(No, None))
}

case class PercentageSplit(
                            percentWithin30Days: Int,
                            percentWithin60Days: Int,
                            percentBeyond60Days: Int
                          ) {
  def total: Int = percentWithin30Days + percentWithin60Days + percentBeyond60Days
}

case class PaymentHistory(
                           averageDaysToPay: Int,
                           percentPaidLaterThanAgreedTerms: Int,
                           percentageSplit: PercentageSplit
                         )

object PaymentHistory {
  def apply(row: PaymentHistoryRow): PaymentHistory =
    PaymentHistory(row.averageDaysToPay, row.percentPaidLaterThanAgreedTerms, PercentageSplit(row.percentInvoicesWithin30Days, row.percentInvoicesWithin60Days, row.percentInvoicesBeyond60Days))
}

case class PaymentTermsChanged(comment: ConditionalText, notified: Option[ConditionalText]) {
  /**
    * If the answer to the comment question is No then remove any answer to the Notified question
    */
  def normalise = this match {
    case PaymentTermsChanged(c@ConditionalText(No, _), _) => PaymentTermsChanged(c, None)
    case _ => this
  }
}

case class PaymentTerms(
                         paymentPeriod: Int,
                         terms: String,
                         maximumContractPeriod: Int,
                         maximumContractPeriodComment: Option[String],
                         paymentTermsChanged: PaymentTermsChanged,
                         paymentTermsComment: Option[String],
                         disputeResolution: String
                       )

object PaymentTerms {
  def apply(row: PaymentTermsRow): PaymentTerms =
    PaymentTerms(row.paymentPeriod, row.paymentTerms, row.maximumContractPeriod, row.maximumContractPeriodComment,
      pt(row),
      row.paymentTermsChangedComment,
      row.disputeResolution
    )

  def pt(row: PaymentTermsRow): PaymentTermsChanged = {
    val comment = ConditionalText(row.paymentTermsChangedComment)
    val notified =
      if (comment.yesNo === Yes) Some(ConditionalText(row.paymentTermsChangedNotifiedComment))
      else None

    PaymentTermsChanged(comment, notified)
  }
}

case class ReportFormModel(
                            reportDates: DateRange,
                            paymentHistory: PaymentHistory,
                            paymentTerms: PaymentTerms,
                            offerEInvoicing: YesNo,
                            offerSupplyChainFinancing: YesNo,
                            retentionChargesInPolicy: YesNo,
                            retentionChargesInPast: YesNo,
                            hasPaymentCodes: ConditionalText
                          )

object ReportFormModel {
  def apply(filed: FiledReport): ReportFormModel = {
    ReportFormModel(
      DateRange(filed.period.startDate, filed.period.endDate),
      PaymentHistory(filed.paymentHistory),
      PaymentTerms(filed.paymentTerms),
      filed.otherInfo.offerEInvoicing,
      filed.otherInfo.offerSupplyChainFinance,
      filed.otherInfo.retentionChargesInPolicy,
      filed.otherInfo.retentionChargesInPast,
      ConditionalText(filed.otherInfo.paymentCodes)
    )
  }

}

case class ReportReviewModel(
                              confirmedBy: String,
                              confirmed: Boolean
                            )



