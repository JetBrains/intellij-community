// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.performanceTesting

import com.intellij.execution.RunManager
import com.intellij.maven.performanceTesting.dto.MavenGoalConfigurationDto
import com.intellij.maven.performanceTesting.utils.MavenConfigurationUtils
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType

/**
 * The command validates a maven goal's settings
 * Argument is serialized [com.intellij.maven.performanceTesting.dto.MavenGoalConfigurationDto] as json
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
    val configurationMame = MavenRunConfigurationType.generateName(project, MavenConfigurationUtils.createRunnerParams(project, settings))
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