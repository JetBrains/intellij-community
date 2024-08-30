// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.swagger

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.util.PlatformUtils
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

internal class OpenApiSuggestionProvider : PluginSuggestionProvider {

  override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
    if (!FileTypeManager.getInstance().isFileOfType(file, YAMLFileType.YML)) return null

    if (isPluginSuggestionDismissed() || tryUltimateIsDisabled()) return null
    if (PlatformUtils.isWriterside()) {
      // WRS-2730 not compatible without `microservices`
      return null
    }

    val requiredPluginId = PluginId.getId(OPENAPI_PLUGIN_ID)
    if (PluginManager.isPluginInstalled(requiredPluginId)) return null

    val thisProductCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode

    val isOpenApiFile = detectOpenApiSpecification(project, file)
    if (!isOpenApiFile) return null

    return OpenApiPluginSuggestion(project, thisProductCode)
  }
}

private class OpenApiPluginSuggestion(val project: Project,
                                      val thisProductCode: String) : PluginSuggestion {
  override val pluginIds: List<String> = listOf(OPENAPI_PLUGIN_ID)

  override fun apply(fileEditor: FileEditor): EditorNotificationPanel {
    val status = if (PluginAdvertiserService.isCommunityIde()) EditorNotificationPanel.Status.Promo else EditorNotificationPanel.Status.Info
    val panel = EditorNotificationPanel(fileEditor, status)

    val suggestedIdeCode = PluginAdvertiserService.getSuggestedCommercialIdeCode(thisProductCode)
    val suggestedCommercialIde = PluginAdvertiserService.getIde(suggestedIdeCode)

    if (suggestedCommercialIde == null) {
      panel.text = IdeBundle.message("plugins.advertiser.plugins.found", OPENAPI_FILES)

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugin.name", OPENAPI_PLUGIN_NAME)) {
        val pluginIds = listOf(OPENAPI_PLUGIN_ID)

        FUSEventSource.EDITOR.logInstallPlugins(pluginIds, project)
        installAndEnable(project, pluginIds.map(PluginId::getId).toSet(), true) {
          EditorNotifications.getInstance(project).updateAllNotifications()
        }
      }
    }
    else {
      panel.text = IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", OPENAPI_FILES, suggestedCommercialIde.name)
      panel.createTryUltimateActionLabel(suggestedCommercialIde, project, PluginId.getId(OPENAPI_PLUGIN_ID))
    }
    
    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
      FUSEventSource.EDITOR.logIgnoreExtension(project)
      dismissPluginSuggestion()
      EditorNotifications.getInstance(project).updateAllNotifications()
    }

    return panel
  }
}

private const val OPENAPI_PLUGIN_ID: String = "com.intellij.swagger"
private const val OPENAPI_PLUGIN_NAME: String = "OpenAPI Specifications"
private const val OPENAPI_FILES: String = "OpenAPI"
private const val OPENAPI_SUGGESTION_DISMISSED_KEY: String = "swagger.suggestion.dismissed"

private fun dismissPluginSuggestion() {
  PropertiesComponent.getInstance().setValue(OPENAPI_SUGGESTION_DISMISSED_KEY, true)
}

private fun isPluginSuggestionDismissed(): Boolean {
  return PropertiesComponent.getInstance().isTrueValue(OPENAPI_SUGGESTION_DISMISSED_KEY)
}

private fun detectOpenApiSpecification(project: Project, file: VirtualFile): Boolean {
  val psiFile = PsiManager.getInstance(project).findFile(file)
  return isOpenApiYaml(psiFile)
}

private fun isOpenApiYaml(file: PsiFile?): Boolean {
  if (file !is YAMLFile) {
    return false
  }

  return CachedValuesManager.getCachedValue(file) {
    CachedValueProvider.Result(computeOpenApiYaml(file), file)
  }
}

private fun computeOpenApiYaml(file: PsiFile?): Boolean {
  return file is YAMLFile && isOpenApiDocument(file.documents.firstOrNull())
}

private fun isOpenApiDocument(document: YAMLDocument?): Boolean {
  val topMapping = document?.topLevelValue as? YAMLMapping ?: return false

  return topMapping.getKeyValueByKeyIgnoreSpaces("openapi") != null
         || topMapping.getKeyValueByKeyIgnoreSpaces("swagger") != null
}

private fun YAMLMapping.getKeyValueByKeyIgnoreSpaces(keyText: String): YAMLKeyValue? {
  return keyValues.firstOrNull { keyText.compareTo(it.keyText.trim()) == 0 }
}