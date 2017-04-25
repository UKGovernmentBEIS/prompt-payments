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

package utils

import javax.inject.Inject

import cats.MonadError
import cats.instances.future._

import scala.concurrent.{ExecutionContext, Future}

class FutureMonadError @Inject()(implicit ec: ExecutionContext) extends MonadError[Future, Throwable] {
  val fe = catsStdInstancesForFuture

  override def flatMap[A, B](fa: Future[A])(f: (A) => Future[B]) = fe.flatMap(fa)(f)

  override def tailRecM[A, B](a: A)(f: (A) => Future[Either[A, B]]) = fe.tailRecM(a)(f)

  override def raiseError[A](e: Throwable) = fe.raiseError(e)

  override def handleErrorWith[A](fa: Future[A])(f: (Throwable) => Future[A]) = fe.handleErrorWith(fa)(f)

  override def pure[A](x: A) = fe.pure(x)
}
