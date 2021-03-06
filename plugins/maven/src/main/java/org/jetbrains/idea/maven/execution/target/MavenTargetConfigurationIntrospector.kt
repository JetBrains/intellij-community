// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.Platform
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.openapi.util.text.StringUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletionStage

class MavenTargetConfigurationIntrospector(private val config: MavenRuntimeTargetConfiguration) : LanguageRuntimeType.Introspector<MavenRuntimeTargetConfiguration> {
  override fun introspect(subject: LanguageRuntimeType.Introspectable): CompletableFuture<MavenRuntimeTargetConfiguration> {
    var isWindows = false
    return subject.promiseExecuteScript("ver")
      .handle { output, _ -> isWindows = output?.contains("Microsoft Windows") ?: false }
      .thenFindMaven(subject, isWindows)
      .thenApply { (mavenHome, versionOutput) ->
        if (versionOutput != null) {
          (mavenHome ?: extractMavenHome(versionOutput))?.let {
            config.homePath = it
            config.versionString = extractMavenVersion(versionOutput) ?: ""
          }
        }
        return@thenApply config
      }
  }

  private fun extractMavenVersion(versionOutput: String): String? {
    val lines = StringUtil.splitByLines(versionOutput, true)
    return lines.find { it.startsWith("Apache Maven ") } ?: lines.firstOrNull()
  }

  private fun extractMavenHome(versionOutput: String): String? {
    val lines = StringUtil.splitByLines(versionOutput, true)
    return lines.find { it.startsWith("Maven home: ") }?.substringAfter("Maven home: ")
  }

  private fun <T> CompletableFuture<T>.thenFindMaven(subject: LanguageRuntimeType.Introspectable,
                                                     isWindows: Boolean): CompletableFuture<Pair<String?, String?>> {
    return thenCompose { promiseMavenVersion(subject, "M2_HOME", isWindows) }
      .thenCompose { it.completeOrElse { promiseMavenVersion(subject, "MAVEN_HOME", isWindows) } }
      .thenCompose { it.completeOrElse { promiseMavenVersion(subject, null, isWindows) } }
  }

  private fun promiseMavenVersion(subject: LanguageRuntimeType.Introspectable,
                                  mavenHomeEnvVariable: String?,
                                  isWindows: Boolean): CompletableFuture<Pair<String?, String?>> {
    if (mavenHomeEnvVariable == null) {
      return subject.promiseExecuteScript("mvn -version").handle { output, _ -> output?.run { null to output } ?: null to null }
    }

    return subject.promiseEnvironmentVariable(mavenHomeEnvVariable).thenCompose { mavenHome ->
      if (mavenHome.isNullOrBlank()) {
        return@thenCompose completedFuture(null)
      }
      else {
        val mavenHomeTrimmed = mavenHome.trim()
        val fileSeparator = if (isWindows) Platform.WINDOWS.fileSeparator else Platform.UNIX.fileSeparator
        val mvnScriptPath = arrayOf(mavenHomeTrimmed, "bin", "mvn").joinToString(fileSeparator.toString())
        return@thenCompose subject.promiseExecuteScript("$mvnScriptPath -version")
          .handle { output, _ -> output?.run { mavenHomeTrimmed to output } }
      }
    }
  }

  private fun <T> T?.completeOrElse(orElse: () -> CompletionStage<T>): CompletionStage<T> = this?.let { completedFuture(this) } ?: orElse()
}