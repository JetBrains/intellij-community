// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import icons.MavenIcons
import org.jetbrains.idea.maven.execution.RunnerBundle
import java.util.function.Supplier

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
                                  targetEnvironmentType: TargetEnvironmentType<*>,
                                  targetSupplier: Supplier<TargetEnvironmentConfiguration>): Configurable {
    return MavenRuntimeTargetUI(config, targetEnvironmentType, targetSupplier, project)
  }

  override fun findLanguageRuntime(target: TargetEnvironmentConfiguration): MavenRuntimeTargetConfiguration? {
    return target.runtimes.findByType()
  }

  override fun createIntrospector(config: MavenRuntimeTargetConfiguration): Introspector<MavenRuntimeTargetConfiguration>? {
    if (config.homePath.isNotBlank()) return null
    return MavenTargetConfigurationIntrospector(config)
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
                                                 "")

    @JvmStatic
    val MAVEN_EXT_CLASS_PATH_VOLUME = VolumeDescriptor(MavenRuntimeType::class.qualifiedName + ":maven.ext.class.path",
                                                       RunnerBundle.message("maven.target.execution.ext.class.path.folder.label"),
                                                       RunnerBundle.message("maven.target.execution.ext.class.path.folder.description"),
                                                       RunnerBundle.message("maven.target.execution.ext.class.path.folder.browsing.title"),
                                                       "")
  }
}