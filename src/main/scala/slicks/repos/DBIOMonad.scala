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

import cats.Monad
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext

object DBIOMonad {
  implicit def dbioMonadInstance(implicit ec: ExecutionContext) = new Monad[DBIO] {
    override def flatMap[A, B](fa: DBIO[A])(f: (A) => DBIO[B]): DBIO[B] = fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: (A) => DBIO[Either[A, B]]): DBIO[B] = f(a).flatMap {
      case Left(a1) => tailRecM(a1)(f)
      case Right(c) => pure(c)
    }

    override def pure[A](x: A): DBIO[A] = DBIO.successful(x)
  }
}
