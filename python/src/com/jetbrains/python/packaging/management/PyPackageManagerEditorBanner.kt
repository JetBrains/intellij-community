// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.impl.EditorHeaderComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.components.TwoSideComponent
import com.jetbrains.python.PyBundle
import com.jetbrains.python.requirements.RequirementsFileType
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.util.function.Function
import javax.swing.JComponent

/**
 * Renders the [PythonPackageManagerAction]s as a horizontal toolbar pinned to the top of the file
 * editor, replacing the icon-only floating context-bar surface. The right edge always carries an
 * [OpenExternalToolsSettingsAction] that opens the External Tools settings page; the watched files
 * (pyproject.toml, hatch.toml, Pipfile, environment.y(a)ml, requirements.txt) additionally show
 * the package-manager-specific actions on the left, each rendered as icon + caption.
 *
 * The visual shape mirrors `JupyterFileEditorToolbar`: an [EditorHeaderComponent] hosting a
 * [TwoSideComponent] with two non-opaque toolbars, so the strip blends with the editor chrome
 * instead of looking like a yellow-style notification banner.
 *
 * The banner is intentionally action-group-driven: adding a new [PythonPackageManagerAction] entry
 * to the `PythonPackageManagerActions` group automatically surfaces it here, so consumers don't
 * need to know about this provider.
 */
@ApiStatus.Internal
internal class PyPackageManagerEditorBanner : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<in FileEditor, out JComponent?>? {
    if (!isWatchedFile(file)) return null
    return Function { fileEditor -> buildPanel(fileEditor) }
  }

  private fun buildPanel(fileEditor: FileEditor): JComponent? {
    val actionManager = ActionManager.getInstance()
    val mainGroup = actionManager.getAction(ACTION_GROUP_ID) as? ActionGroup ?: return null

    val leftToolbar = actionManager.createActionToolbar(PLACE, mainGroup, true).apply {
      targetComponent = fileEditor.component
      setReservePlaceAutoPopupIcon(false)
      component.isOpaque = false
    }
    val rightGroup = DefaultActionGroup(OpenExternalToolsSettingsAction())
    val rightToolbar = actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, rightGroup, true).apply {
      targetComponent = fileEditor.component
      setReservePlaceAutoPopupIcon(false)
      component.isOpaque = false
    }

    return EditorHeaderComponent().apply {
      add(TwoSideComponent(leftToolbar.component, rightToolbar.component), BorderLayout.CENTER)
    }
  }

  private fun isWatchedFile(file: VirtualFile): Boolean {
    return file.fileType is RequirementsFileType || file.name in WATCHED_NAMES
  }

  /**
   * Always-visible right-edge action: opens Settings → Languages & Frameworks → Python → Tools →
   * External Tools so the user can manage the per-tool enable / lookup / path overrides.
   */
  private class OpenExternalToolsSettingsAction : DumbAwareAction(
    PyBundle.messagePointer("action.OpenExternalToolsSettings.text"),
    PyBundle.messagePointer("action.OpenExternalToolsSettings.description"),
    AllIcons.General.GearPlain,
  ) {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = e.project != null
      e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      ShowSettingsUtilImpl.showSettingsDialog(project, EXTERNAL_TOOLS_CONFIGURABLE_ID, null)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  companion object {
    private const val ACTION_GROUP_ID: String = "PythonPackageManagerActions"
    private const val PLACE: String = "PyPackageManagerEditorBanner"
    private const val EXTERNAL_TOOLS_CONFIGURABLE_ID: String = "python.external.tools.group.settings"
    private val WATCHED_NAMES: Set<String> = setOf(
      PY_PROJECT_TOML,
      "hatch.toml",
      "Pipfile",
      "Pipfile.lock",
      "environment.yml",
      "environment.yaml",
    )
  }
}
