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

package actions

import javax.inject.Inject

import play.api.mvc.Results.Redirect
import play.api.mvc.{ActionBuilder, Request, Result, WrappedRequest}
import repos.{SessionId, SessionRepo}

import scala.concurrent.{ExecutionContext, Future}

case class SessionRequest[A](sessionId: SessionId, request: Request[A]) extends WrappedRequest[A](request)

/**
  * This action checks for a `sessionId` attribute on the Play session. If none is found then an id is created (as a UUID).
  * The value of this `sessionId` is lifted to a property of the `SessionRequest` so that action handlers can access it
  * easily.
  *
  * If a sessionId is found on the request, but does not exist in the SessionService, then it is assumed that the
  * session has timed out and the user is redirected to an appropriate error page.
  */
class SessionAction @Inject()(sessionService: SessionRepo[Future])(implicit ec: ExecutionContext) extends ActionBuilder[SessionRequest] {

  import SessionAction.sessionIdKey

  override def invokeBlock[A](request: Request[A], block: (SessionRequest[A]) => Future[Result]): Future[Result] = {
    for {
      sessionId <- retrieveOrCreateSessionId(request, sessionIdKey)
      exists <- sessionService.exists(sessionId)
      result <- if (exists) refreshAndInvoke(request, block, sessionId) else timeout
    } yield result

  }

  private val timeout = Future.successful(Redirect(controllers.routes.ErrorController.sessionTimeout()))

  private def retrieveOrCreateSessionId[A](request: Request[A], sessionIdKey: String): Future[SessionId] = {
    request.session.get(sessionIdKey) match {
      case Some(id) => Future.successful(SessionId(id))
      case None => sessionService.newSession
    }
  }

  private def refreshAndInvoke[A](request: Request[A], block: (SessionRequest[A]) => Future[Result], sessionId: SessionId) = {
    sessionService.refresh(sessionId).flatMap { _ => proceed(request, block, sessionId) }
  }

  private def proceed[A](request: Request[A], block: (SessionRequest[A]) => Future[Result], sessionId: SessionId) = {
    block(SessionRequest(sessionId, request)).map { result =>
      result.addingToSession(sessionIdKey -> sessionId.id)(request)
    }
  }
}

object SessionAction {
  val sessionIdKey = "sessionId"
}