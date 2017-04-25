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

package slicks.repos

import java.util.UUID
import javax.inject.{Inject, Named}

import com.github.tminglei.slickpg.{PgDateSupportJoda, PgPlayJsonSupport}
import dbrows.SessionRow
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{JsObject, _}
import repos.{SessionId, SessionRepo}
import slicks.DBBinding
import slicks.helpers.RowBuilders
import slicks.modules.SessionModule
import utils.TimeSource

import scala.concurrent.{ExecutionContext, Future}

class SessionTable @Inject()(val dbConfigProvider: DatabaseConfigProvider, timeSource: TimeSource, @Named("session timeout") sessionTimeoutInMinutes: Int)(implicit ec: ExecutionContext)
  extends DBBinding
    with SessionRepo[Future]
    with SessionModule
    with PgDateSupportJoda
    with PgPlayJsonSupport
    with RowBuilders {

  import api._

  override def pgjson: String = "jsonb"

  def sessionQ(sessionId: Rep[SessionId]) = sessionTable.filter(s => s.id === sessionId && s.expiresAt >= timeSource.now())

  val sessionC = Compiled(sessionQ _)

  override def put[T: Writes](sessionId: SessionId, key: String, value: T): Future[Unit] = db.run {
    sessionC(sessionId).result.headOption.flatMap {
      case Some(s) =>
        val newSessionData = s.sessionData + (key -> Json.toJson(value))
        sessionC(sessionId).update(s.copy(sessionData = newSessionData))

      case None =>
        val row = SessionRow(sessionId, sessionExpiryTime, JsObject(Seq(key -> Json.toJson(value))))
        sessionTable += row
    }.transactionally.map(_ => ())
  }

  private def sessionExpiryTime = timeSource.now().plusMinutes(sessionTimeoutInMinutes)

  override def get[T: Reads](sessionId: SessionId, key: String): Future[Option[T]] = db.run {
    sessionC(sessionId).result.headOption.map {
      _.flatMap { row =>
        (row.sessionData \ key).validateOpt[T] match {
          case JsSuccess(t, _) => t
          case JsError(_) => None
        }
      }
    }
  }

  override def get[T: Reads](sessionId: SessionId): Future[Option[T]] = db.run {
    sessionC(sessionId).result.headOption.map {
      _.flatMap { row =>
        row.sessionData.validateOpt[T] match {
          case JsSuccess(t, _) => t
          case JsError(errs) =>
            Logger.debug(errs.toString)
            None
        }
      }
    }
  }

  override def clear(sessionId: SessionId, key: String): Future[Unit] = db.run {
    sessionC(sessionId).result.headOption.map {
      case Some(s) =>
        val newSessionData = s.sessionData - key
        sessionC(sessionId).update(s.copy(sessionData = newSessionData))

      case None => DBIO.successful(())
    }.transactionally.map(_ => ())
  }

  /**
    * Refresh the expiry time of the session to be the current time plus the
    * timeout in minutes
    */
  override def refresh(sessionId: SessionId): Future[Unit] = db.run {
    Logger.debug(s"asked to refresh $sessionId")
    sessionC(sessionId).result.headOption.flatMap {
      case Some(s) => sessionC(sessionId).update(s.copy(expiresAt = sessionExpiryTime))
      case None => DBIO.successful(())
    }.transactionally.map(_ => ())
  }

  override def removeExpired(): Future[Unit] = db.run {
    sessionTable.filter(_.expiresAt <= timeSource.now()).delete.map(_ => ())
  }

  override def newSession: Future[SessionId] = db.run {
    val id = SessionId(UUID.randomUUID().toString)
    (sessionTable += SessionRow(id, sessionExpiryTime, JsObject(Seq()))).map { _ => id }
  }

  override def exists(sessionId: SessionId): Future[Boolean] = db.run {
    sessionC(sessionId).result.headOption.map(_.isDefined)
  }


}