/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.akka.http.server

import akka.actor.{ ReflectiveDynamicAccess, ExtendedActorSystem }
import com.typesafe.config.Config

case class AkkaHttpServerExtensionSettings(
  includeTraceTokenHeader: Boolean,
  traceTokenHeaderName: String,
  nameGenerator: NameGenerator)

object AkkaHttpServerExtensionSettings {
  def apply(config: Config): AkkaHttpServerExtensionSettings = {
    val httpConfig = config.getConfig("kamon.akka.http.server")

    val includeTraceTokenHeader: Boolean = httpConfig.getBoolean("automatic-trace-token-propagation")
    val traceTokenHeaderName: String = httpConfig.getString("trace-token-header-name")

    val nameGeneratorFQN = httpConfig.getString("name-generator")
    val nameGenerator: NameGenerator = new ReflectiveDynamicAccess(getClass.getClassLoader)
      .createInstanceFor[NameGenerator](nameGeneratorFQN, Nil).get // let's bubble up any problems.

    AkkaHttpServerExtensionSettings(includeTraceTokenHeader, traceTokenHeaderName, nameGenerator)
  }
}
