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
 * WITHOUT WARRANTIED OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package v2.endpoints

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status
import play.api.libs.ws.{WSRequest, WSResponse}
import support.IntegrationBaseSpec
import v2.fixtures.EopsObligationsFixture
import v2.stubs._

class EopsDeclarationISpec extends IntegrationBaseSpec {

  private trait Test {

    val nino: String = "AA123456A"
    val from: String = "2017-04-06"
    val to: String = "2018-04-05"

    val requestJson =
      """
        |{
        |"finalised": true
        |}
      """.stripMargin

    def setupStubs(): StubMapping

    def request(): WSRequest = {
      setupStubs()
        buildRequest(s"/2.0/ni/$nino/uk-properties/end-of-period-statements/from/$from/to/$to")
    }
  }

  "Calling the EOPS declaration endpoint" should {

    "return a 204 status code" when {

      "submitted with valid data" in new Test {

        override def setupStubs(): StubMapping = {
          AuditStub.audit()
          AuthStub.authorised()
          MtdIdLookupStub.ninoFound(nino)
          EopsDeclarationStub.successfulEopsDeclaration(nino, from, to)
        }

        val response: WSResponse = await(request().post(requestJson))
        response.status shouldBe Status.OK
        response.json shouldBe EopsObligationsFixture.EOPSSuccess
      }
    }
  }
}
