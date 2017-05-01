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

package filters

import java.io.File

import akka.actor.Cancellable
import akka.stream.{Attributes, ClosedShape, Graph, Materializer}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}
import play.api.mvc.{Headers, RequestHeader, Result, Results}
import play.api.test.FakeRequest
import play.api.{Environment, Mode}
import play.mvc.Http.HeaderNames

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContextExecutor, Future}

class TLSFilterTest extends WordSpecLike with Matchers with ScalaFutures {

  val method = "GET"
  val httpsProto = "https"
  val notHttpsProto = "not https"

  "urlFor" should {
    val hostName = "host"
    "convert an empty uri to a root https url" in {
      val rh = FakeRequest(method, "", Headers(HeaderNames.HOST -> hostName), "")
      TLSFilter.urlFor(rh) shouldBe s"$httpsProto://$hostName"
    }

    "convert a root uri to a root https url" in {
      val rh = FakeRequest(method, "/", Headers(HeaderNames.HOST -> hostName), "")
      TLSFilter.urlFor(rh) shouldBe s"$httpsProto://$hostName"
    }
  }

  "forwardedFromHttps" should {
    "be true if X-Forwarded-Proto is 'https'" in {
      val rh = FakeRequest(method, "", Headers(HeaderNames.X_FORWARDED_PROTO -> httpsProto), "")
      TLSFilter.forwardedFromHttps(rh) shouldBe true
    }

    "be false if X-Forwarded-Proto is present but not 'https'" in {
      val rh = FakeRequest(method, "", Headers(HeaderNames.X_FORWARDED_PROTO -> notHttpsProto), "")
      TLSFilter.forwardedFromHttps(rh) shouldBe false
    }

    "be true if X-Forwarded-Proto is not present" in {
      val rh = FakeRequest(method, "", Headers(), "")
      TLSFilter.forwardedFromHttps(rh) shouldBe false
    }
  }

  implicit val mat = NoMaterializer

  import scala.concurrent.ExecutionContext.Implicits.global

  // The header is checked immediately so no need to wait for the returned future.
  def checkHeader(expected: RequestHeader): RequestHeader => Future[Result] = { actual =>
    actual shouldBe expected
    Future.successful(Results.Ok(""))
  }

  val shouldNotBeCalled: RequestHeader => Future[Result] = { actual =>
    fail("should not have been called")
  }

  "apply" should {
    "process the request unchanged if not in PROD mode" in {
      val rh = FakeRequest(method, "", Headers(), "")
      val filter = new TLSFilter(Environment(new File(""), this.getClass.getClassLoader, Mode.Dev))
      filter.apply(checkHeader(rh))(rh)
    }

    "process the request unchanged if not in PROD mode but already secure" in {
      val rh = FakeRequest(method, "", Headers(), "", secure = true)
      val filter = new TLSFilter(Environment(new File(""), this.getClass.getClassLoader, Mode.Prod))
      filter.apply(checkHeader(rh))(rh)
    }

    "process the request unchanged if not in PROD mode but forwarded from https" in {
      val rh = FakeRequest(method, "", Headers(HeaderNames.X_FORWARDED_PROTO -> httpsProto), "")
      val filter = new TLSFilter(Environment(new File(""), this.getClass.getClassLoader, Mode.Prod))
      filter.apply(checkHeader(rh))(rh)
    }

    "redirect to https in Prod mode when not already secure. Next filter in chain should not be called" in {
      val hostName = "test.com"
      val headers = Headers(HeaderNames.HOST -> hostName)
      val updatedUri = s"$httpsProto://$hostName/test"

      val rh = FakeRequest(method, "/test", headers, "")
      val filter = new TLSFilter(Environment(new File(""), this.getClass.getClassLoader, Mode.Prod))
      val f = filter.apply(shouldNotBeCalled)(rh)
      f.futureValue shouldBe Results.MovedPermanently(updatedUri)
    }
  }
}

object NoMaterializer extends Materializer {
  override def withNamePrefix(name: String): Materializer =
    throw new UnsupportedOperationException("NoMaterializer cannot be named")

  override def materialize[Mat](runnable: Graph[ClosedShape, Mat]): Mat =
    throw new UnsupportedOperationException("NoMaterializer cannot materialize")

  override def materialize[Mat](runnable: Graph[ClosedShape, Mat], initialAttributes: Attributes): Mat =
    throw new UnsupportedOperationException("NoMaterializer cannot materialize")

  override def executionContext: ExecutionContextExecutor =
    throw new UnsupportedOperationException("NoMaterializer does not provide an ExecutionContext")

  def scheduleOnce(delay: FiniteDuration, task: Runnable): Cancellable =
    throw new UnsupportedOperationException("NoMaterializer cannot schedule a single event")

  def schedulePeriodically(initialDelay: FiniteDuration, interval: FiniteDuration, task: Runnable): Cancellable =
    throw new UnsupportedOperationException("NoMaterializer cannot schedule a repeated event")
}