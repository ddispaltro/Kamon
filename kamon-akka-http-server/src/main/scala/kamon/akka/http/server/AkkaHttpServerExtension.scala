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

import akka.actor._
import akka.event._
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Host
import kamon.Kamon
import kamon.metric.Entity
import kamon.util.http.HttpServerMetrics
import kamon.util.logger.LazyLogger

object AkkaHttpServerExtension {
  val log = LazyLogger("kamon.akka.http.server.AkkaHttpServerExtension")
  val settings = AkkaHttpServerExtensionSettings(Kamon.config)
  val SegmentLibraryName = "akka-http-client"

  val httpServerMetrics = {
    val entity = Entity("akka-http-server", HttpServerMetrics.category)
    Kamon.metrics.entity(AkkaHttpServerMetrics, entity)
  }

  def generateTraceName(request: HttpRequest): String =
    settings.nameGenerator.generateTraceName(request)

  def generateRequestLevelApiSegmentName(request: HttpRequest): String =
    settings.nameGenerator.generateRequestLevelApiSegmentName(request)

  def generateHostLevelApiSegmentName(request: HttpRequest): String =
    settings.nameGenerator.generateHostLevelApiSegmentName(request)
}

trait NameGenerator {
  def generateTraceName(request: HttpRequest): String
  def generateRequestLevelApiSegmentName(request: HttpRequest): String
  def generateHostLevelApiSegmentName(request: HttpRequest): String
}

class DefaultNameGenerator extends NameGenerator {
  def generateRequestLevelApiSegmentName(request: HttpRequest): String = {
    val uriAddress = request.uri.authority.host.address
    if (uriAddress.equals("")) hostFromHeaders(request).getOrElse("unknown-host") else uriAddress
  }

  def generateHostLevelApiSegmentName(request: HttpRequest): String =
    hostFromHeaders(request).getOrElse("unknown-host")

  def generateTraceName(request: HttpRequest): String =
    "UnnamedTrace"

  private def hostFromHeaders(request: HttpRequest): Option[String] =
    request.header[Host].map(_.host.toString())

}
