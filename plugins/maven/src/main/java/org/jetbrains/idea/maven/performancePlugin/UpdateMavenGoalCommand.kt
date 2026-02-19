// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.SingleConfigurationConfigurable
import com.intellij.execution.ui.RunnerAndConfigurationSettingsEditor
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.run.configuration.MavenRunConfigurationSettingsEditor
import org.jetbrains.idea.maven.performancePlugin.dto.MavenGoalConfigurationDto
import org.jetbrains.idea.maven.performancePlugin.utils.MavenConfigurationUtils.createRunnerParams
import org.jetbrains.idea.maven.project.MavenGeneralSettings

/**
 * The command updates a maven goal's settings
 * Argument is serialized [MavenGoalConfigurationDto] as json
 */
class UpdateMavenGoalCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "updateMavenGoal"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override fun getName(): String {
    return NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val settings = deserializeOptionsFromJson(extractCommandArgument(PREFIX), MavenGoalConfigurationDto::class.java)
    val params = createRunnerParams(project, settings)

    val configSettings = MavenRunConfigurationType.createRunnerAndConfigurationSettings(MavenGeneralSettings(),
                                                                                        null,
                                                                                        params,
                                                                                        project,
                                                                                        MavenRunConfigurationType.generateName(project, params),
                                                                                        false)


    withContext(Dispatchers.EDT) {
      val configurable = writeIntentReadAction { SingleConfigurationConfigurable.editSettings<RunConfiguration>(configSettings, null) }
      val editor = (configurable.editor as RunnerAndConfigurationSettingsEditor)
      val configEditorRef = editor.javaClass.getDeclaredField("myConfigurationEditor")
      configEditorRef.isAccessible = true
      val configEditor = configEditorRef.get(editor) as MavenRunConfigurationSettingsEditor
      configEditor.builder.editors
        .filter { it is SettingsEditorFragment<*, *> }
        .map { it as SettingsEditorFragment<*, *> }
        .first {
          it.id == "maven.general.options.group"
        }
        .children
        .first { it.id == "maven.user.settings.fragment" }
        .apply {
          isSelected = true
          (component.components.first { it::class == TextFieldWithBrowseButton::class } as TextFieldWithBrowseButton).text = settings.settingsFilePath
        }

      configurable.apply()
    }

  }
}