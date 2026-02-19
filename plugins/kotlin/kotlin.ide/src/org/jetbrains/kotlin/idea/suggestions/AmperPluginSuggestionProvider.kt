// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.suggestions

import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.buildSuggestionForFileIfNeeded
import com.intellij.openapi.vfs.VirtualFile

private const val AMPER_PLUGIN_ID = "org.jetbrains.amper"
private const val AMPER_PLUGIN_NAME = "Amper"
private const val AMPER_FILE_LABEL = "Amper"
private const val AMPER_PLUGIN_SUGGESTION_KEY = "amper.plugin.suggestion.dismissed"

private const val MODULE_YAML_FILE_NAME: String = "module.yaml"
private const val MODULE_AMPER_FILE_NAME: String = "module.amper"
private const val PROJECT_YAML_FILE_NAME: String = "project.yaml"
private const val PROJECT_AMPER_FILE_NAME: String = "project.amper"
private const val PLUGIN_YAML_FILE_NAME: String = "plugin.yaml"
private const val TEMPLATE_FILE_SUFFIX = ".module-template.yaml"

internal class AmperPluginSuggestionProvider : PluginSuggestionProvider {

    override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
        if (!file.isAmperFile()) return null

        return buildSuggestionForFileIfNeeded(
            project,
            AMPER_PLUGIN_ID,
            AMPER_PLUGIN_NAME,
            AMPER_FILE_LABEL,
            AMPER_PLUGIN_SUGGESTION_KEY
        )
    }

    private fun VirtualFile.isAmperFile(): Boolean = isAmperModuleFile() || isAmperProjectFile() || isAmperTemplateFile() || isAmperPluginFile()

    private fun VirtualFile.isAmperModuleFile(): Boolean = name.equals(MODULE_YAML_FILE_NAME, ignoreCase = true) || name.equals(MODULE_AMPER_FILE_NAME, ignoreCase = true)

    private fun VirtualFile.isAmperTemplateFile(): Boolean = name.endsWith(TEMPLATE_FILE_SUFFIX, ignoreCase = true)

    private fun VirtualFile.isAmperProjectFile(): Boolean = name.equals(PROJECT_YAML_FILE_NAME, ignoreCase = true) || name.equals(PROJECT_AMPER_FILE_NAME, ignoreCase = true)

    private fun VirtualFile.isAmperPluginFile(): Boolean = name.equals(PLUGIN_YAML_FILE_NAME, ignoreCase = true)
}
