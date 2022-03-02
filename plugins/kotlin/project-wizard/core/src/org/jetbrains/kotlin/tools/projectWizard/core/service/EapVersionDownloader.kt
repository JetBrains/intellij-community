// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core.service

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
    fun getLatestEapVersion() = downloadVersions(EAP_URL).firstOrNull()

    private fun downloadPage(url: String): TaskResult<String> = safe {
      BufferedReader(InputStreamReader(URL(url).openStream())).lines().collect(Collectors.joining("\n"))
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
    private val EAP_URL = "https://plugins.gradle.org/m2/org/jetbrains/kotlin/jvm/org.jetbrains.kotlin.jvm.gradle.plugin/"

    @NonNls
    private val versionRegexp = """href="([^"\\]+)"""".toRegex()
}