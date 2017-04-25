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

package repos

import com.google.inject.ImplementedBy
import dbrows.ConfirmationPendingRow
import models.ReportId
import org.joda.time.LocalDateTime
import slicks.repos.ConfirmationTable
import uk.gov.service.notify.{NotificationClientException, SendEmailResponse}

@ImplementedBy(classOf[ConfirmationTable])
trait ConfirmationRepo[F[_]] {
  /**
    * Look for a single confirmation record that is ready to be sent and, lock it for the configured
    * locking period. If one is found, return both the confirmation detail and the report it relates
    * to.
    */
  def findUnconfirmedAndLock(): F[Option[(ConfirmationPendingRow, FiledReport)]]


  /**
    * Record that a confirmation was successfully sent for the given report.
    */
  def confirmationSent(reportId: ReportId, when: LocalDateTime, response: SendEmailResponse): F[Unit]

  /**
    * Record that the attempt to send the confirmation resulted in a permanent failure.
    */
  def confirmationFailed(reportId: ReportId, when: LocalDateTime, ex: NotificationClientException): F[Unit]
}



