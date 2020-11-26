// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.Platform
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import icons.MavenIcons
import org.jetbrains.idea.maven.execution.RunnerBundle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.*

class MavenRuntimeType : LanguageRuntimeType<MavenRuntimeTargetConfiguration>(TYPE_ID) {
  override val icon = MavenIcons.ExecuteMavenGoal

  @NlsSafe
  override val displayName = "Maven"

  override val configurableDescription = "Maven configuration"

  override val launchDescription = "Run Maven goal"

  override fun isApplicableTo(runConfig: RunnerAndConfigurationSettings) = true

  override fun createDefaultConfig() = MavenRuntimeTargetConfiguration()

  override fun createSerializer(config: MavenRuntimeTargetConfiguration): PersistentStateComponent<*> = config

  override fun createConfigurable(project: Project,
                                  config: MavenRuntimeTargetConfiguration,
                                  target: TargetEnvironmentConfiguration): Configurable {
    return MavenRuntimeTargetUI(config, target, project)
  }

  override fun findLanguageRuntime(target: TargetEnvironmentConfiguration): MavenRuntimeTargetConfiguration? {
    return target.runtimes.findByType()
  }

  override fun createIntrospector(config: MavenRuntimeTargetConfiguration): Introspector<MavenRuntimeTargetConfiguration>? {
    if (config.homePath.isNotBlank()) return null

    return object : Introspector<MavenRuntimeTargetConfiguration> {
      override fun introspect(subject: Introspectable): CompletableFuture<MavenRuntimeTargetConfiguration> {
        var isWindows = false
        val introspectionPromise = subject.promiseExecuteScript("ver")
          .thenApply { isWindows = it?.contains("Microsoft Windows") ?: false }
          .thenCompose { promiseMavenVersion(subject, "M2_HOME", isWindows) }
          .thenCompose { it?.let { completedFuture(it) } ?: promiseMavenVersion(subject, "MAVEN_HOME", isWindows) }
          .thenCompose { it?.let { completedFuture(it) } ?: promiseMavenVersion(subject, null, isWindows) }
          .thenApply { (mavenHome, versionOutput) ->
            if (versionOutput != null) {
              val lines = StringUtil.splitByLines(versionOutput, true)
              (mavenHome ?: lines.find { it.startsWith("Maven home: ") }?.substringAfter("Maven home: "))?.let {
                config.homePath = it
                config.versionString = lines.firstOrNull() ?: ""
              }
            }
          }
        return introspectionPromise.thenApply { config }
      }
    }
  }

  private fun promiseMavenVersion(subject: Introspectable,
                                  mavenHomeEnvVariable: String?,
                                  isWindows: Boolean): CompletableFuture<Pair<String?, String?>> {
    if (mavenHomeEnvVariable == null) {
      return subject.promiseExecuteScript("mvn -version").thenCompose { completedFuture(null to it) }
    }

    return subject.promiseEnvironmentVariable(mavenHomeEnvVariable).thenCompose { mavenHome ->
      if (mavenHome == null) {
        return@thenCompose completedFuture(null)
      }
      else {
        val fileSeparator = if (isWindows) Platform.WINDOWS.fileSeparator else Platform.UNIX.fileSeparator
        val mvnScriptPath = arrayOf(mavenHome, "bin", "mvn").joinToString(fileSeparator.toString())
        return@thenCompose subject.promiseExecuteScript("$mvnScriptPath -version")
          .thenCompose { completedFuture(mavenHome to it) }
      }
    }
  }

  override fun duplicateConfig(config: MavenRuntimeTargetConfiguration): MavenRuntimeTargetConfiguration =
    duplicatePersistentComponent(this, config)

  companion object {
    @JvmStatic
    val TYPE_ID = "MavenRuntime"

    @JvmStatic
    val PROJECT_FOLDER_VOLUME = VolumeDescriptor(MavenRuntimeType::class.qualifiedName + ":projectFolder",
                                                 RunnerBundle.message("maven.target.execution.project.folder.label"),
                                                 RunnerBundle.message("maven.target.execution.project.folder.description"),
                                                 RunnerBundle.message("maven.target.execution.project.folder.browsing.title"),
                                                 "/project")

    @JvmStatic
    val MAVEN_EXT_CLASS_PATH_VOLUME = VolumeDescriptor(MavenRuntimeType::class.qualifiedName + ":maven.ext.class.path",
                                                       RunnerBundle.message("maven.target.execution.ext.class.path.folder.label"),
                                                       RunnerBundle.message("maven.target.execution.ext.class.path.folder.description"),
                                                       RunnerBundle.message("maven.target.execution.ext.class.path.folder.browsing.title"),
                                                       "")
  }
}