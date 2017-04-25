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

package actors

import javax.inject.Inject

import akka.actor.Actor
import play.api.Logger
import repos.ConfirmationRepo
import services.live.ConfirmationDeliveryServiceImpl
import services.NotifyService

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Success

/**
  * This actor manages the sending of confirmation emails via the GDS Notify service.
  *
  * On startup it schedules a recurring event that sends a `'poll` message to itself.
  *
  * On receipt of a `'poll` message it calls the delivery service to attempt delivery
  * of any pending confirmation.
  *
  * If delivery was attempted (regardless of the outcome) then the actor immediately sends
  * itself another `'poll` message to see if there are any more confirmations to send.
  *
  * If no delivery was attempted it takes no action and waits for the next scheduled event.
  *
  */
class ConfirmationActor @Inject()(confirmationRepo: ConfirmationRepo[Future], notifyService: NotifyService[Future]) extends Actor {

  /**
    * Use the execution context, and hence the thread pool, for this actor for scheduling
    * the poll events and for making the network calls to deliver notifications.
    */
  implicit val ec = context.dispatcher

  val deliveryService = new ConfirmationDeliveryServiceImpl(confirmationRepo, notifyService)

  Logger.debug(s"ConfirmationActor started")
  Logger.debug(s"dispatcher is ${context.dispatcher}")

  context.system.scheduler.schedule(1 second, 10 seconds, self, 'poll)

  def receive = {
    case 'poll => deliveryService.attemptDelivery.onComplete {
      // some delivery attempt was made. Immediately see if there are more pending
      case Success(Some(_)) => self ! 'poll
      case _ => // do nothing
    }
  }
}

