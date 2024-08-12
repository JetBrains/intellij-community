// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.suggestions

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyCellUtil
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.pyi.PyiFile
import org.jetbrains.annotations.Nls

private const val PYCHARM_PRO_SUGGESTION_DISMISSED_KEY: String = "pycharm.pro.suggestion.dismissed"

private val PACKAGES_TO_ADVERTISE = hashSetOf<String>("pytorch", "sklearn", "pandas",
                                                      "numpy", "tensorflow", "keras",
                                                      "torch", "matplotlib", "scipy",
                                                      "cv2", "torchvision",
                                                      "sqlalchemy", "seaborn", "django",
                                                      "flask", "fastapi")


internal class PycharmProSuggestionProvider : PluginSuggestionProvider {

  override fun getSuggestion(project: Project, vFile: VirtualFile): PluginSuggestion? {
    if (!vFile.isPythonFile()
        || isDismissed()
        || tryUltimateIsDisabled()
        || isPyCharmProOrIdeaUltimate()
        || FileIndexFacade.getInstance(project).isInLibraryClasses(vFile)) {
      return null
    }

    val psiFile = PsiManager.getInstance(project).findFile(vFile)

    if (psiFile == null || psiFile is PyiFile || psiFile !is PyFile) return null

    if (PyCellUtil.hasCells(psiFile)) {
      return PycharmProSuggestion(PyBundle.message("advertiser.code.cells.supported.by.pro"), project)
    }

    val supportedPackage = findPackageSupportedByPro(psiFile)
    if (supportedPackage != null) {
      return PycharmProSuggestion(PyBundle.message("advertiser.package.supported.by.pro", supportedPackage), project)
    }

    return null
  }

  private fun findPackageSupportedByPro(psiFile: PyFile): String? {
    val imports = psiFile.fromImports.mapNotNull { it.importSourceQName?.firstComponent }.toSet() +
                  psiFile.importTargets.mapNotNull { it.importedQName?.firstComponent }.toSet()
    return PACKAGES_TO_ADVERTISE.intersect(imports).firstOrNull()
  }

  private fun isPyCharmProOrIdeaUltimate(): Boolean {
    val productCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode
    return productCode == "PY" || productCode == "IU"
  }

  private fun isDismissed(): Boolean = PropertiesComponent.getInstance().isTrueValue(PYCHARM_PRO_SUGGESTION_DISMISSED_KEY)

  private fun VirtualFile.isPythonFile() = this.fileType == PythonFileType.INSTANCE


  private class PycharmProSuggestion(
    @Nls private val label: String,
    private val project: Project,
    override val pluginIds: List<String> = emptyList<String>(),
  ) : PluginSuggestion {

    override fun apply(fileEditor: FileEditor): EditorNotificationPanel? {
      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Promo)
      setupCommercialIdeSuggestion(panel, label)
      return panel
    }

    private fun setupCommercialIdeSuggestion(panel: EditorNotificationPanel, @Nls label: String) {
      val ultimateIde = PluginAdvertiserService.pyCharmProfessional
      panel.text = label
      panel.icon(AllIcons.Ultimate.PycharmLock)
      panel.createTryUltimateActionLabel(ultimateIde, project)

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
        FUSEventSource.EDITOR.logIgnoreExtension(project)
        dismissSuggestion()
        EditorNotifications.getInstance(project).updateAllNotifications()
      }
    }

    private fun dismissSuggestion() {
      PropertiesComponent.getInstance().setValue(PYCHARM_PRO_SUGGESTION_DISMISSED_KEY, true)
    }
  }
}