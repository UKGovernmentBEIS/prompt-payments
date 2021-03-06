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

import enumeratum.EnumEntry.Lowercase
import enumeratum.{Enum, EnumEntry, PlayJsonEnum}
import utils.YesNo.{No, Yes}

sealed trait YesNo extends EnumEntry with Lowercase {
  def toBoolean: Boolean

  def fold[T](yes: => T, no: => T): T = this match {
    case Yes => yes
    case No  => no
  }
}

object YesNo extends Enum[YesNo] with PlayJsonEnum[YesNo] with EnumFormatter[YesNo] {
  //noinspection TypeAnnotation
  override def values = findValues

  def fromBoolean(b: Boolean): YesNo = if (b) Yes else No

  case object Yes extends YesNo {
    override def toBoolean = true
  }

  case object No extends YesNo {
    override def toBoolean = false
  }

}
