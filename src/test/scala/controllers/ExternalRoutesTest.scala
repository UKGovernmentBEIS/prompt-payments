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

package controllers

import config.RoutesConfig
import org.scalatest.{Matchers, WordSpecLike}

class ExternalRoutesTest extends WordSpecLike with Matchers {

  "ExternalRoutes" should {
    "generate a router for localhost when no search host is provided" in {
      val r = (new ExternalRoutes(new RoutesConfig(None))) ("localhost:9000")
      r.root shouldBe "http://localhost:9001"
    }

    "generate a router for the supplied search host" in {
      val r = (new ExternalRoutes(new RoutesConfig(Some("otherhost")))) ("thishost")
      r.root shouldBe "https://otherhost"
    }

    "generate a router for the corresponding host when the supplied host matches the heroku pattern" in {
      val r = (new ExternalRoutes(new RoutesConfig(None))) ("beis-ppr-dev.herokuapp.com")
      r.root shouldBe "https://beis-spp-dev.herokuapp.com"
    }

    "generate a router for the configured host even if the supplied host matches the heroku pattern" in {
      val r = (new ExternalRoutes(new RoutesConfig(Some("otherhost")))) ("beis-ppr-dev.herokuapp.com")
      r.root shouldBe "https://otherhost"
    }

  }

}
