// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.welcomeScreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.editorTab.LearnIdeEditorTab
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTab
import com.intellij.pycharm.community.ide.impl.PycharmCommunityIdeImplIcons
import com.intellij.pycharm.community.ide.impl.miscProject.MiscFileType
import com.intellij.pycharm.community.ide.impl.miscProject.PyMiscService
import com.intellij.pycharm.community.ide.impl.miscProject.impl.MiscScriptFileType
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcomeBundle
import com.intellij.util.application
import com.jetbrains.python.icons.PythonIcons
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.theme.colorPalette
import java.util.function.Supplier
import javax.swing.Icon

class PyCharmWelcomeRightTabContentProvider : WelcomeRightTabContentProvider {
  override val backgroundImageVectorLight: ImageVector = PyCharmWelcomeRightTabLightBackground
  override val backgroundImageVectorDark: ImageVector = PyCharmWelcomeRightTabDarkBackground

  override val fileTypeIcon: Icon = PythonIcons.Python.Pycharm
  override val title: Supplier<String> = PyWelcomeBundle.lazyMessage("non.modal.welcome.screen.title")
  override val secondaryTitle: Supplier<String> = PyWelcomeBundle.lazyMessage("non.modal.welcome.screen.secondary.title")

  @Composable
  override fun getFeatureButtonModels(project: Project): List<WelcomeRightTabContentProvider.FeatureButtonModel> {
    return listOfNotNull(
      newScriptFeatureButtonModel(project),
      newNotebookFeatureButtonModel(project),
      importFileFeatureButtonModel(project),
      learnFeatureButtonModel(project),
      pluginsFeatureButtonModel(project)
    )
  }

  private fun newScriptFeatureButtonModel(project: Project): WelcomeRightTabContentProvider.FeatureButtonModel {
    val scriptMiscType = MiscScriptFileType
    return WelcomeRightTabContentProvider.FeatureButtonModel(
      text = PyWelcomeBundle.message("non.modal.welcome.screen.feature.script"),
      icon = PathIconKey("icons/pythonFile.svg", PycharmCommunityIdeImplIcons::class.java)
    ) {
      PyMiscService.getInstance().createMiscProject(project, scriptMiscType)
    }
  }

  private fun newNotebookFeatureButtonModel(project: Project): WelcomeRightTabContentProvider.FeatureButtonModel? {
    val notebookMiscType = MiscFileType.EP.extensionList.find { it.id == "newNotebook" } ?: return null
    return WelcomeRightTabContentProvider.FeatureButtonModel(
      text = PyWelcomeBundle.message("non.modal.welcome.screen.feature.notebook"),
      icon = PathIconKey("icons/jupyterNotebook.svg", PycharmCommunityIdeImplIcons::class.java),
    ) {
      PyMiscService.getInstance().createMiscProject(project, notebookMiscType)
    }
  }

  private fun learnFeatureButtonModel(project: Project): WelcomeRightTabContentProvider.FeatureButtonModel {
    return WelcomeRightTabContentProvider.FeatureButtonModel(
      text = PyWelcomeBundle.message("non.modal.welcome.screen.feature.learn"),
      icon = PathIconKey("icons/learn.svg", PycharmCommunityIdeImplIcons::class.java)
    ) {
      LearnIdeEditorTab.openLearnIdeInEditorTab(project)
    }
  }

  private fun importFileFeatureButtonModel(project: Project): WelcomeRightTabContentProvider.FeatureButtonModel {
    return WelcomeRightTabContentProvider.FeatureButtonModel(
      text = PyWelcomeBundle.message("non.modal.welcome.screen.feature.import.file"),
      icon = PathIconKey("icons/importFile.svg", PycharmCommunityIdeImplIcons::class.java),
    ) {
      val action = ActionManager.getInstance().getAction("OpenFile")
      val event = createEvent(action, SimpleDataContext.getProjectContext(project), null, ActionPlaces.WELCOME_SCREEN, ActionUiKind.NONE, null)
      action.actionPerformed(event)
    }
  }

  // Not yet implemented
  @Suppress("unused")
  private fun junieFeatureButtonModel(project: Project): WelcomeRightTabContentProvider.FeatureButtonModel {
    return WelcomeRightTabContentProvider.FeatureButtonModel(
      text = PyWelcomeBundle.message("non.modal.welcome.screen.feature.start.with.ai"),
      icon = PathIconKey("icons/junie.svg", PycharmCommunityIdeImplIcons::class.java),
    ) {

    }
  }

  @Composable
  private fun pluginsFeatureButtonModel(project: Project): WelcomeRightTabContentProvider.FeatureButtonModel {
    return WelcomeRightTabContentProvider.FeatureButtonModel(
      text = PyWelcomeBundle.message("non.modal.welcome.screen.feature.plugins"),
      tint = blueTint,
      icon = PathIconKey("expui/nodes/plugin.svg", PycharmCommunityIdeImplIcons::class.java),
    ) {
      application.invokeLater {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, PluginManagerConfigurable::class.java)
      }
    }
  }

  @get:Composable
  private val blueTint: Color
    get() = WelcomeScreenRightTab.color(dark = JewelTheme.colorPalette.blueOrNull(8),
                                        light = JewelTheme.colorPalette.blueOrNull(4),
                                        fallback = Color(0xFF548AF7))
}
