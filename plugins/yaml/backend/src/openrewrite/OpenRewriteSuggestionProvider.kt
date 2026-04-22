// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.openrewrite

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.createTryUltimateActionLabel
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.tryUltimateIsDisabled
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

internal class OpenRewriteSuggestionProvider : PluginSuggestionProvider {

  override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
    if (!FileTypeManager.getInstance().isFileOfType(file, YAMLFileType.YML)) return null

    if (isPluginSuggestionDismissed() || tryUltimateIsDisabled()) return null

    val requiredPluginId = PluginId.getId(OPENREWRITE_PLUGIN_ID)
    if (PluginManager.isPluginInstalled(requiredPluginId)) return null

    if (!PluginManagerCore.isPluginInstalled(PluginManagerCore.JAVA_PLUGIN_ID)) return null

    val thisProductCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode

    val isOpenRewriteFile = detectOpenRewriteRecipe(project, file)
    if (!isOpenRewriteFile) return null

    return OpenRewritePluginSuggestion(project, thisProductCode)
  }
}

private class OpenRewritePluginSuggestion(val project: Project,
                                          val thisProductCode: String) : PluginSuggestion {
  override val pluginIds: List<String> = listOf(OPENREWRITE_PLUGIN_ID)

  override fun apply(fileEditor: FileEditor): EditorNotificationPanel {
    val status = if (PluginAdvertiserService.isCommunityIde()) EditorNotificationPanel.Status.Promo else EditorNotificationPanel.Status.Info
    val panel = EditorNotificationPanel(fileEditor, status)

    val suggestedIdeCode = PluginAdvertiserService.getSuggestedCommercialIdeCode(thisProductCode)
    val suggestedCommercialIde = PluginAdvertiserService.getIde(suggestedIdeCode)

    if (suggestedCommercialIde == null) {
      panel.text = IdeBundle.message("plugins.advertiser.plugins.found", 1, OPENREWRITE_FILES)

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugin.name", OPENREWRITE_PLUGIN_NAME)) {
        val pluginIds = listOf(OPENREWRITE_PLUGIN_ID)

        FUSEventSource.EDITOR.logInstallPlugins(pluginIds, project)
        installAndEnable(project, pluginIds.map(PluginId::getId).toSet(), true) {
          EditorNotifications.getInstance(project).updateAllNotifications()
        }
      }
    }
    else {
      panel.text = IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", OPENREWRITE_FILES, suggestedCommercialIde.name)
      panel.createTryUltimateActionLabel(suggestedCommercialIde, project, PluginId.getId(OPENREWRITE_PLUGIN_ID))
    }

    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
      FUSEventSource.EDITOR.logIgnoreExtension(project)
      dismissPluginSuggestion()
      EditorNotifications.getInstance(project).updateAllNotifications()
    }

    return panel
  }
}

private const val OPENREWRITE_PLUGIN_ID: String = "com.intellij.openRewrite"
private const val OPENREWRITE_PLUGIN_NAME: String = "OpenRewrite"
private const val OPENREWRITE_FILES: String = "OpenRewrite"
private const val OPENREWRITE_SUGGESTION_DISMISSED_KEY: String = "open.rewrite.suggestion.dismissed"

private val REWRITE_TYPE_REGEX: Regex = Regex("specs\\.openrewrite\\.org/\\w+/(recipe|style)")

private fun dismissPluginSuggestion() {
  PropertiesComponent.getInstance().setValue(OPENREWRITE_SUGGESTION_DISMISSED_KEY, true)
}

private fun isPluginSuggestionDismissed(): Boolean {
  return PropertiesComponent.getInstance().isTrueValue(OPENREWRITE_SUGGESTION_DISMISSED_KEY)
}

private fun detectOpenRewriteRecipe(project: Project, file: VirtualFile): Boolean {
  val psiFile = PsiManager.getInstance(project).findFile(file)
  return isOpenRewriteRecipe(psiFile)
}

private fun isOpenRewriteRecipe(file: PsiFile?): Boolean {
  if (file !is YAMLFile) {
    return false
  }

  return CachedValuesManager.getCachedValue(file) {
    CachedValueProvider.Result(computeIsOpenRewriteRecipe(file), file)
  }
}

private fun computeIsOpenRewriteRecipe(yamlFile: YAMLFile): Boolean {
  return isOpenRewriteDocument(yamlFile.documents.firstOrNull())
}

private fun isOpenRewriteDocument(document: YAMLDocument?): Boolean {
  val topMapping = document?.topLevelValue as? YAMLMapping ?: return false
  val typeKv = topMapping.getKeyValueByKeyIgnoreSpaces("type") ?: return false
  val scalarText = (typeKv.value as? YAMLScalar)?.textValue ?: return false
  return REWRITE_TYPE_REGEX.matches(scalarText)
}

private fun YAMLMapping.getKeyValueByKeyIgnoreSpaces(keyText: String): YAMLKeyValue? {
  return keyValues.firstOrNull { keyText.compareTo(it.keyText.trim()) == 0 }
}
