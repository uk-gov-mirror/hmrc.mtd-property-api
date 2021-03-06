/*
 * Copyright 2021 HM Revenue & Customs
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

package v2.config

import com.typesafe.config.Config
import javax.inject.{Inject, Singleton}
import play.api.{ConfigLoader, Configuration}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

trait AppConfig {
  def desBaseUrl: String

  def mtdIdBaseUrl: String

  def desEnv: String

  def desToken: String

  def confidenceLevelConfig: ConfidenceLevelConfig
}

@Singleton
class AppConfigImpl @Inject()(servicesConfig: ServicesConfig,
                              configuration: Configuration) extends AppConfig {

  val mtdIdBaseUrl: String = servicesConfig.baseUrl("mtd-id-lookup")
  val desBaseUrl: String = servicesConfig.baseUrl("des")
  val desEnv: String = servicesConfig.getString("microservice.services.des.env")
  val desToken: String = servicesConfig.getString("microservice.services.des.token")
  val confidenceLevelConfig: ConfidenceLevelConfig = configuration.get[ConfidenceLevelConfig](s"api.confidence-level-check")
}

case class ConfidenceLevelConfig(definitionEnabled: Boolean, authValidationEnabled: Boolean)
object ConfidenceLevelConfig {
  implicit val configLoader: ConfigLoader[ConfidenceLevelConfig] = (rootConfig: Config, path: String) => {
    val config = rootConfig.getConfig(path)
    ConfidenceLevelConfig(
      definitionEnabled = config.getBoolean("definition.enabled"),
      authValidationEnabled = config.getBoolean("auth-validation.enabled")
    )
  }
}
