/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package v2.mocks

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

trait MockHttpClient extends MockFactory {

  val mockHttpClient: HttpClient = mock[HttpClient]

  object MockedHttpClient {
    def get[T](url: String): CallHandler[Future[T]] = {
      (mockHttpClient.GET(_: String)(_: HttpReads[T], _: HeaderCarrier, _: ExecutionContext))
        .expects(url, *, *, *)
    }

    def postEmpty[T](url: String): CallHandler[Future[T]] = {
      (mockHttpClient.POSTEmpty[T](_: String)(_: HttpReads[T], _: HeaderCarrier, _: ExecutionContext))
        .expects(url, *, *, *)
    }
  }
}
