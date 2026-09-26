// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.suggestions

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.buildSuggestionIfNeeded
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.pyi.PyiFile

private const val MARIMO_PLUGIN_ID: String = "io.marimo.notebook"
private const val MARIMO_PLUGIN_NAME: String = "marimo"
private const val MARIMO_PLUGIN_SUGGESTION_DISMISSED_KEY: String = "marimo.plugin.suggestion.dismissed"

internal class MarimoSuggestionProvider : PluginSuggestionProvider {
  override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
    if (file.fileType != PythonFileType.INSTANCE || FileIndexFacade.getInstance(project).isInLibraryClasses(file)) {
      return null
    }

    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile == null || psiFile is PyiFile || psiFile !is PyFile || !psiFile.importsMarimo()) return null

    return buildSuggestionIfNeeded(
      project,
      pluginIds = listOf(MARIMO_PLUGIN_ID),
      pluginName = MARIMO_PLUGIN_NAME,
      suggestionText = PyBundle.message("advertiser.marimo.plugin.suggestion.text"),
      suggestionDismissKey = MARIMO_PLUGIN_SUGGESTION_DISMISSED_KEY,
    )
  }

  private fun PyFile.importsMarimo(): Boolean {
    val imports = fromImports.mapNotNull { it.importSourceQName?.firstComponent } +
                  importTargets.mapNotNull { it.importedQName?.firstComponent }
    return imports.contains("marimo")
  }
}