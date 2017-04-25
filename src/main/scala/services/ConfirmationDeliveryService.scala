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

package services

import com.google.inject.ImplementedBy
import models.ReportId
import services.live.ConfirmationDeliveryServiceImpl

trait DeliveryOutcome

case class ConfirmationSent(reportId: ReportId) extends DeliveryOutcome

case class ConfirmationFailed(reportId: ReportId) extends DeliveryOutcome

@ImplementedBy(classOf[ConfirmationDeliveryServiceImpl])
trait ConfirmationDeliveryService[F[_]] {
  /**
    * This function will look for a single pending confirmation and attempt to deliver it.
    *
    * If a confirmation was found and delivery was attempted then, regardless of the outcome of the
    * delivery attempt, a DeliveryOutcome will be returned with the id of the report.
    */
  def attemptDelivery: F[Option[DeliveryOutcome]]
}
