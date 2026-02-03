// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.execution.RunManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.performancePlugin.dto.MavenGoalConfigurationDto
import org.jetbrains.idea.maven.performancePlugin.utils.MavenConfigurationUtils.createRunnerParams

/**
 * The command validates a maven goal's settings
 * Argument is serialized [MavenGoalConfigurationDto] as json
 */
class ValidateMavenGoalCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "validateMavenGoal"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override fun getName(): String {
    return NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val settings = deserializeOptionsFromJson(extractCommandArgument(PREFIX), MavenGoalConfigurationDto::class.java)

    val list = RunManager.getInstance(project).getConfigurationsList(MavenRunConfigurationType.getInstance())
    val configurationMame = MavenRunConfigurationType.generateName(project, createRunnerParams(project, settings))
    val configuration = list.firstOrNull { it.name == configurationMame }
    if (configuration == null) {
      throw IllegalArgumentException("There is no configuration with name $configurationMame")
    }

    val actualSettingsFile = (configuration as MavenRunConfiguration).generalSettings?.userSettingsFile
    if (actualSettingsFile != settings.settingsFilePath) {
      throw IllegalArgumentException("User settings file ${settings.settingsFilePath} does not match configuration $actualSettingsFile.")
    }
  }
}