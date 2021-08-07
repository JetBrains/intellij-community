// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.core.TaskResult
import org.jetbrains.kotlin.tools.projectWizard.core.asNullable
import org.jetbrains.kotlin.tools.projectWizard.core.compute
import org.jetbrains.kotlin.tools.projectWizard.core.safe
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.stream.Collectors

object EapVersionDownloader {
    fun getLatestEapVersion() = downloadVersionFromMavenCentral(EAP_URL).firstOrNull()
    fun getLatestDevVersion() = downloadVersions(DEV_URL).firstOrNull()

    private fun downloadPage(url: String): TaskResult<String> = safe {
      BufferedReader(InputStreamReader(URL(url).openStream())).lines().collect(Collectors.joining("\n"))
    }

    @Suppress("SameParameterValue")
    private fun downloadVersionFromMavenCentral(url: String) = compute {
      val (text) = downloadPage(url)
      val (versionString) = parseLatestVersionFromJson(text)
      if (versionString.isNotEmpty())
        listOf(Version.fromString(versionString))
      else
        emptyList()
    }.asNullable.orEmpty()

    private fun parseLatestVersionFromJson(text: String) = safe {
      val json = JsonParser.parseString(text) as JsonObject
      json.get("response").asJsonObject.get("docs").asJsonArray.get(0).asJsonObject.get("latestVersion").asString
    }

    @Suppress("SameParameterValue")
    private fun downloadVersions(url: String): List<Version> = compute {
      val (text) = downloadPage(url)
      versionRegexp.findAll(text)
        .map { it.groupValues[1].removeSuffix("/") }
        .filter { it.isNotEmpty() && it[0].isDigit() }
        .map { Version.fromString(it) }
        .toList()
        .asReversed()
    }.asNullable.orEmpty()

    @NonNls
    private val EAP_URL = "https://search.maven.org/solrsearch/select?q=g:org.jetbrains.kotlin%20AND%20a:kotlin-gradle-plugin"

    @NonNls
    private val DEV_URL = "https://dl.bintray.com/kotlin/kotlin-dev/org/jetbrains/kotlin/jvm/org.jetbrains.kotlin.jvm.gradle.plugin/"

    @NonNls
    private val versionRegexp = """href="([^"\\]+)"""".toRegex()
}