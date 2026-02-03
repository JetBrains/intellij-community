package org.jetbrains.yaml.helm

import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.buildSuggestionForFileIfNeeded
import com.intellij.openapi.vfs.VirtualFile

class GoTemplatePluginSuggestionProvider : PluginSuggestionProvider {
  private val GO_TEMPLATE_PLUGIN_ID = "org.jetbrains.plugins.go-template"
  private val GO_TEMPLATE_PLUGIN_NAME = "Go Template"
  private val GO_TEMPLATE_FILE_LABEL = "Go Template"
  private val GO_TEMPLATE_SUGGESTION_DISMISS_KEY = "go.template.suggestion.dismissed"

  override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
    if (!isHelmTemplateFile(file)) return null

    return buildSuggestionForFileIfNeeded(
      project,
      GO_TEMPLATE_PLUGIN_ID,
      GO_TEMPLATE_PLUGIN_NAME,
      GO_TEMPLATE_FILE_LABEL,
      GO_TEMPLATE_SUGGESTION_DISMISS_KEY
    )
  }
}