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

package slicks.modules

import com.github.tminglei.slickpg.PgPlayJsonSupport
import dbrows.SessionRow
import org.joda.time.LocalDateTime
import play.api.libs.json.JsObject
import repos.SessionId
import slicks.DBBinding
import slicks.helpers.PlayJsonMappers

trait SessionModule extends DBBinding with PlayJsonMappers {
  self: PgPlayJsonSupport =>

  import api._

  type SessionQuery = Query[SessionTable, SessionRow, Seq]

  implicit def sessionIdMapper: BaseColumnType[SessionId] = MappedColumnType.base[SessionId, String](_.id, SessionId)

  class SessionTable(tag: Tag) extends Table[SessionRow](tag, "session") {
    def id = column[SessionId]("id", O.Length(36), O.PrimaryKey)

    def expiresAt = column[LocalDateTime]("expires_at")

    def sessionData = column[JsObject]("session_data")

    def * = (id, expiresAt, sessionData) <> (SessionRow.tupled, SessionRow.unapply)
  }

  lazy val sessionTable = TableQuery[SessionTable]

  override def schema = super.schema ++ sessionTable.schema
}