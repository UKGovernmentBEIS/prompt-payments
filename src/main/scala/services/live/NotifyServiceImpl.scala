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

package services.live

import java.util
import javax.inject.Inject

import akka.actor.ActorSystem
import config.NotifyConfig
import services.NotifyService
import uk.gov.service.notify.{NotificationClient, SendEmailResponse}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

class NotifyServiceImpl @Inject()(actorSystem: ActorSystem, config: NotifyConfig) extends NotifyService[Future] {
  val key = config.apiKey

  /**
    * @param ec - the `sendEmail` call is blocking on the network call (because we're using the Java implementation
    *           of the GOV Notify API) so accept an execution context that can put these calls onto a separate thread
    *           pool from Play's client pool.
    * @return
    */
  override def sendEmail(recipient: String, params: Map[String, String])(implicit ec: ExecutionContext): Future[SendEmailResponse] = {
    val client = new NotificationClient(key)

    val m: util.Map[String, String] = params
    val jParams = new util.HashMap[String, String]()
    jParams.putAll(m)
    Future.successful(client.sendEmail(config.templateId, recipient, jParams, ""))
  }
}
