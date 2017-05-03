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

import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric._
import org.scalactic.TripleEquals._

object Ordinals {

  type PosInt = Refined[Int, Positive]

  private val wordOrdinals = Seq("zeroth", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth", "thirteenth")

  def forNumber(n: PosInt): String = {
    n match {
      case _ if n.value < wordOrdinals.length => wordOrdinals(n.value)
      case _ if n.value % 10 === 1 => s"${n.value}st"
      case _ if n.value % 10 === 2 => s"${n.value}nd"
      case _ if n.value % 10 === 3 => s"${n.value}rd"
      case _ => s"${n.value}th"
    }
  }

  def forNumber(i: Int): Option[String] =
    refineV[Positive](i).right.toOption.map(forNumber)
}


