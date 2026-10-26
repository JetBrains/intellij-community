// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.actions

import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.RegistryManager
import org.intellij.plugins.markdown.editor.livepreview.isLivePreviewEnabled
import org.intellij.plugins.markdown.editor.livepreview.setLivePreviewEnabledness
import org.intellij.plugins.markdown.lang.supportsMarkdown
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview

internal class MarkdownDiffLivePreviewAction : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabledAndVisible = isDiffLivePreviewAvailable() && getMarkdownEditors(event).isNotEmpty()
  }

  override fun isSelected(event: AnActionEvent): Boolean {
    val editors = getMarkdownEditors(event)
    return editors.isNotEmpty() && editors.all { it.isLivePreviewEnabled() }
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    PropertiesComponent.getInstance().setValue(PROPERTY, state, false)
    getMarkdownEditors(event).forEach { it.setLivePreviewEnabledness(state) }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  private fun getMarkdownEditors(event: AnActionEvent): List<Editor> {
    val viewer = event.getData(DiffDataKeys.DIFF_VIEWER) as? EditorDiffViewer ?: return emptyList()
    return viewer.editors.filter { !it.isDisposed && it.supportsMarkdown() }
  }
}

/** Holds the last state that the user chose. */
private const val PROPERTY = "markdown.diff.live.preview"

internal fun isDiffLivePreviewEnabled(): Boolean =
  isDiffLivePreviewAvailable() && PropertiesComponent.getInstance().getBoolean(PROPERTY)

internal fun isDiffLivePreviewAvailable(): Boolean =
  RegistryManager.getInstance().`is`(MarkdownEditorWithPreview.LIVE_PREVIEW_REGISTRY_KEY)
