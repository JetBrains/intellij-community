// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import icons.MavenIcons
import org.jetbrains.idea.maven.execution.RunnerBundle
import java.util.concurrent.CompletableFuture

class MavenRuntimeType : LanguageRuntimeType<MavenRuntimeTargetConfiguration>(TYPE_ID) {
  override val icon = MavenIcons.ExecuteMavenGoal

  override val displayName = "Maven"

  override val configurableDescription = "Configure Maven"

  override val launchDescription = "Run Maven goal"

  override fun isApplicableTo(runConfig: RunnerAndConfigurationSettings) = true

  override fun createDefaultConfig() = MavenRuntimeTargetConfiguration()

  override fun createSerializer(config: MavenRuntimeTargetConfiguration): PersistentStateComponent<*> = config

  override fun createConfigurable(project: Project,
                                  config: MavenRuntimeTargetConfiguration,
                                  target: TargetEnvironmentConfiguration): Configurable {
    return MavenRuntimeTargetUI(config, target)
  }

  override fun findLanguageRuntime(target: TargetEnvironmentConfiguration): MavenRuntimeTargetConfiguration? {
    return target.runtimes.findByType<MavenRuntimeTargetConfiguration>()
  }

  override fun createIntrospector(config: MavenRuntimeTargetConfiguration): Introspector<MavenRuntimeTargetConfiguration>? {
    if (config.homePath.isNotBlank() && config.versionString.isNotBlank()) return null

    return object : Introspector<MavenRuntimeTargetConfiguration> {
      override fun introspect(subject: Introspectable): CompletableFuture<MavenRuntimeTargetConfiguration> {
        val home = if (config.homePath.isBlank()) {
          subject.promiseEnvironmentVariable("M2_HOME")
            .thenApply {
              it?.let { config.homePath = it }
            }
        }
        else {
          Introspector.DONE
        }

        val version = if (config.versionString.isBlank()) {
          subject.promiseExecuteScript("mvn -version")
            .thenApply { output ->
              output?.let { StringUtil.splitByLines(output, true) }
                ?.firstOrNull() // todo more accurate maven version command output parsing
                ?.let { config.versionString = it }
            }
        }
        else {
          Introspector.DONE
        }

        return CompletableFuture.allOf(home, version)
          .thenApply { config }
      }
    }
  }

  companion object {
    @JvmStatic
    val TYPE_ID = "MavenRuntime"

    @JvmStatic
    val PROJECT_FOLDER_VOLUME = VolumeDescriptor(MavenRuntimeType::class.qualifiedName + ":projectFolder",
                                                 RunnerBundle.message("maven.target.execution.project.folder.label"),
                                                 RunnerBundle.message("maven.target.execution.project.folder.description"),
                                                 "/project")

    @JvmStatic
    val MAVEN_EXT_CLASS_PATH_VOLUME = VolumeDescriptor(MavenRuntimeType::class.qualifiedName + ":maven.ext.class.path",
                                                       RunnerBundle.message("maven.target.execution.ext.class.path.folder.label"),
                                                       RunnerBundle.message("maven.target.execution.ext.class.path.folder.description"),
                                                       "")
  }
}