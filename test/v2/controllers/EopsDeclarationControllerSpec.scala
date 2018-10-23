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

package v2.controllers

import java.time.LocalDate

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsJson, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import v2.mocks.requestParsers.MockEopsDeclarationRequestDataParser
import v2.mocks.services.{MockEnrolmentsAuthService, MockEopsDeclarationService, MockMtdIdLookupService}
import v2.models.EopsDeclarationSubmission
import v2.models.errors.SubmitEopsDeclarationErrors._
import v2.models.errors._
import v2.models.inbound.EopsDeclarationRequestData

import scala.concurrent.Future

class EopsDeclarationControllerSpec extends ControllerBaseSpec
  with MockEopsDeclarationService
  with MockEnrolmentsAuthService
  with MockMtdIdLookupService
  with MockEopsDeclarationRequestDataParser {


  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val requestJson =
    """
      |{
      |"finalised": true
      |}
    """.stripMargin

  private val invalidRequestJson =
    """
      |{
      |"finalised": false
      |}
    """.stripMargin


  class Test {
    MockedEnrolmentsAuthService.authoriseUser()
    MockedMtdIdLookupService.lookup("AA123456A")
      .returns(Future.successful(Right("test-mtd-id")))
    lazy val testController = new EopsDeclarationController(mockEnrolmentsAuthService,
      mockMtdIdLookupService,
      mockRequestDataParser,
      mockEopsDeclarationService)
  }

  val nino: String = "AA123456A"
  val start: String = "2018-01-01"
  val end: String = "2018-12-31"

  "Submit EOPS declaration" should {

    "return a 204 response" when {
      "a valid NINO, from and to date, with declaration as true is passed" in new Test {

        val eopsDeclarationRequestData = EopsDeclarationRequestData(nino, start, end, AnyContentAsJson(Json.parse(requestJson)))
        val eopsDeclarationSubmission = EopsDeclarationSubmission(Nino(nino), LocalDate.parse(start), LocalDate.parse(end))

        MockedEopsDeclarationRequestDataParser.parseRequest(eopsDeclarationRequestData)
          .returns(Right(eopsDeclarationSubmission))

        MockedEopsDeclarationService.submitDeclaration(eopsDeclarationSubmission)
          .returns(Future.successful(None))

        private val response: Future[Result] =
          testController.submit(nino, start, end)(fakePostRequest[JsValue](Json.parse(requestJson)))

        status(response) shouldBe NO_CONTENT
      }
    }

    "return a 403 (Forbidden) error" when {
      "a valid NINO, from and to date, with declaration as false is passed" in new Test {

        val eopsDeclarationRequestData = EopsDeclarationRequestData(nino, start, end, AnyContentAsJson(Json.parse(invalidRequestJson)))
        val eopsDeclarationSubmission = EopsDeclarationSubmission(Nino(nino), LocalDate.parse(start), LocalDate.parse(end))

        MockedEopsDeclarationRequestDataParser.parseRequest(eopsDeclarationRequestData)
          .returns(Right(eopsDeclarationSubmission))

        MockedEopsDeclarationService.submitDeclaration(eopsDeclarationSubmission)
          .returns(Future.successful(Some(ErrorWrapper(NotFinalisedDeclaration, None))))

        private val response: Future[Result] =
          testController.submit(nino, start, end)(fakePostRequest[JsValue](Json.parse(invalidRequestJson)))

        status(response) shouldBe FORBIDDEN
        contentAsJson(response) shouldBe Json.toJson(NotFinalisedDeclaration)
      }
    }


    "return a error" when {
      "a valid NINO, start and end date, with no declaration is passed" in new Test {

        val eopsDeclarationRequestData = EopsDeclarationRequestData(nino, start, end, AnyContentAsJson(Json.obj()))

        MockedEopsDeclarationRequestDataParser.parseRequest(eopsDeclarationRequestData)
          .returns(Left(ErrorWrapper(BadRequestError, None)))

        private val response: Future[Result] =
          testController.submit(nino, start, end)(fakeRequest.withBody(Json.obj()))

        status(response) shouldBe BAD_REQUEST
        contentAsJson(response) shouldBe Json.toJson(BadRequestError)
      }
    }


    "return validation failed errors 400 (Bad Request)" when {

      val eopsErrors = Seq(
        InvalidStartDateError,
        InvalidEndDateError,
        RangeEndDateBeforeStartDateError,
        BadRequestError,
        NinoFormatError
      )

      for (error <- eopsErrors) {
        eopsDeclarationValidationScenarios(error, BAD_REQUEST)
      }
    }


    "return business failed errors 400 (Bad Request)" when {

      val eopsErrors = Seq(EarlySubmissionError, LateSubmissionError)

      for (error <- eopsErrors) {
        eopsDeclarationValidationScenarios(error, BAD_REQUEST)
      }
    }


    "return a 404 (Not Found) error" when {
      eopsDeclarationBusinessScenarios(NotFoundError, NOT_FOUND)
    }

    "return a 500 (ISE)" when {
      eopsDeclarationBusinessScenarios(DownstreamError, INTERNAL_SERVER_ERROR)
    }


    "return error 403 (Forbidden)" when {

      val eopsErrors = Seq(NotFinalisedDeclaration, ConflictError, RuleClass4Over16, RuleClass4PensionAge,
        RuleFhlPrivateUseAdjustment, RuleNonFhlPrivateUseAdjustment,
        RuleMismatchStartDate, RuleMismatchEndDate, RuleConsolidatedExpenses)

      for (error <- eopsErrors) {
        eopsDeclarationBusinessScenarios(error, FORBIDDEN)
      }
    }


    "return multiple errors 400 (Bad Request)" when {

      "validation is failed for more than one scenarios" in new Test {

        val eopsDeclarationRequestData = EopsDeclarationRequestData(nino, start, end, AnyContentAsJson(Json.parse(invalidRequestJson)))

        MockedEopsDeclarationRequestDataParser.parseRequest(eopsDeclarationRequestData)
          .returns(Left(ErrorWrapper(BadRequestError, Some(Seq(InvalidStartDateError, RangeEndDateBeforeStartDateError)))))

        private val response: Future[Result] =
          testController.submit(nino, start, end)(fakePostRequest[JsValue](Json.parse(invalidRequestJson)))

        status(response) shouldBe BAD_REQUEST
        contentAsJson(response) shouldBe Json.toJson(ErrorWrapper(BadRequestError, Some(Seq(InvalidStartDateError, RangeEndDateBeforeStartDateError))))
      }
    }

    "return multiple bvr errors 403 (Forbidden)" when {

      "business validation is failed for more than one scenarios" in new Test {

        val eopsDeclarationRequestData = EopsDeclarationRequestData(nino, start, end, AnyContentAsJson(Json.parse(requestJson)))
        val eopsDeclarationSubmission = EopsDeclarationSubmission(Nino(nino), LocalDate.parse(start), LocalDate.parse(end))

        MockedEopsDeclarationRequestDataParser.parseRequest(eopsDeclarationRequestData)
          .returns(Right(eopsDeclarationSubmission))

        MockedEopsDeclarationService.submitDeclaration(eopsDeclarationSubmission)
          .returns(Future.successful(Some(ErrorWrapper(BVRError, Some(Seq(RuleClass4Over16, RuleClass4PensionAge))))))

        private val response: Future[Result] =
          testController.submit(nino, start, end)(fakePostRequest[JsValue](Json.parse(requestJson)))

        status(response) shouldBe FORBIDDEN
        contentAsJson(response) shouldBe Json.toJson(ErrorWrapper(BVRError, Some(Seq(RuleClass4Over16, RuleClass4PensionAge))))
      }
    }
    "return a single error with 403 (Forbidden)" when {
      "business validation has failed with just one error" in new Test {

        val eopsDeclarationRequestData = EopsDeclarationRequestData(nino, start, end, AnyContentAsJson(Json.parse(requestJson)))
        val eopsDeclarationSubmission = EopsDeclarationSubmission(Nino(nino), LocalDate.parse(start), LocalDate.parse(end))

        MockedEopsDeclarationRequestDataParser.parseRequest(eopsDeclarationRequestData)
          .returns(Right(eopsDeclarationSubmission))

        MockedEopsDeclarationService.submitDeclaration(eopsDeclarationSubmission)
          .returns(Future.successful(Some(ErrorWrapper(RuleClass4Over16, None))))

        private val response: Future[Result] =
          testController.submit(nino, start, end)(fakePostRequest[JsValue](Json.parse(requestJson)))

        status(response) shouldBe FORBIDDEN
        contentAsJson(response) shouldBe Json.toJson(ErrorWrapper(RuleClass4Over16, None))
      }
    }
  }

  def eopsDeclarationValidationScenarios(error: MtdError, expectedStatus: Int): Unit = {
    s"returned a ${error.code} error" in new Test {

      val eopsDeclarationRequestData = EopsDeclarationRequestData(nino, start, end, AnyContentAsJson(Json.parse(requestJson)))
      val eopsDeclarationSubmission = EopsDeclarationSubmission(Nino(nino), LocalDate.parse(start), LocalDate.parse(end))

      MockedEopsDeclarationRequestDataParser.parseRequest(eopsDeclarationRequestData)
        .returns(Right(eopsDeclarationSubmission))

      MockedEopsDeclarationService.submitDeclaration(eopsDeclarationSubmission)
        .returns(Future.successful(Some(ErrorWrapper(error, None))))

      val response: Future[Result] = testController.submit(nino, start, end)(fakePostRequest[JsValue](Json.parse(requestJson)))
      status(response) shouldBe expectedStatus
      contentAsJson(response) shouldBe Json.toJson(error)
    }
  }


  def eopsDeclarationBusinessScenarios(error: MtdError, expectedStatus: Int): Unit = {
    s"returned a ${error.code} error" in new Test {

      val eopsDeclarationRequestData = EopsDeclarationRequestData(nino, start, end, AnyContentAsJson(Json.parse(requestJson)))
      val eopsDeclarationSubmission = EopsDeclarationSubmission(Nino(nino), LocalDate.parse(start), LocalDate.parse(end))

      MockedEopsDeclarationRequestDataParser.parseRequest(eopsDeclarationRequestData)
        .returns(Right(eopsDeclarationSubmission))

      MockedEopsDeclarationService.submitDeclaration(EopsDeclarationSubmission(Nino(nino),
        LocalDate.parse(start), LocalDate.parse(end)))
        .returns(Future.successful(Some(ErrorWrapper(error, None))))

      val response: Future[Result] = testController.submit(nino, start, end)(fakePostRequest[JsValue](Json.parse(requestJson)))
      status(response) shouldBe expectedStatus
      contentAsJson(response) shouldBe Json.toJson(error)
    }
  }

}