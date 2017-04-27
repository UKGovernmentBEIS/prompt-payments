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

case class PageNumber(value: Int) extends AnyVal {
  def +(i: Int): PageNumber = PageNumber(value + i)

  def -(i: Int): PageNumber = PageNumber(value - i)
}

case class PageSize(value: Int) extends AnyVal

case class PagedResults[T](items: Seq[T], pageNumber: PageNumber, pageSize: PageSize, totalResults: Int) {
  val pageCount: Int = (totalResults / pageSize.value.toDouble).ceil.toInt

  private def isValidRange(pageNumber: Int) = pageNumber <= pageCount && pageNumber >= 1

  def canPage: Boolean = canGoBack || canGoNext

  def canGoBack: Boolean = canGo(pageNumber.value - 1)

  def canGoNext: Boolean = canGo(pageNumber.value + 1)

  def canGo(n: Int): Boolean = isValidRange(n)
}


object PagedResults {
  def page[T](items: Seq[T], pageNumber: PageNumber, pageSize: PageSize): PagedResults[T] = {
    PagedResults(items.drop((pageNumber.value - 1) * pageSize.value).take(pageSize.value), pageNumber, pageSize, items.length)
  }
}