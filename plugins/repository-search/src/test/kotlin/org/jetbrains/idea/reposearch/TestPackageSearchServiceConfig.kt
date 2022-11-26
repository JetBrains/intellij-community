package org.jetbrains.idea.reposearch

import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.http.Headers
import io.ktor.http.URLProtocol
import org.jetbrains.idea.packagesearch.PackageSearchServiceConfig
import kotlin.time.Duration.Companion.seconds

object TestPackageSearchServiceConfig : PackageSearchServiceConfig {

  override val host = "test"

  override val timeout
    get() = 15.seconds

  override val userAgent: String
    get() = "TEST"

  override val protocol = URLProtocol.HTTP

  override val headers
    get() = Headers.Empty

  override val logLevel = LogLevel.ALL

  override val logger = Logger.SIMPLE
}