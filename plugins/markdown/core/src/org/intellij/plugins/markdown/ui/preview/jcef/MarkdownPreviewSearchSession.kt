// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.jcef

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.SearchReplaceComponent
import com.intellij.find.SearchSession
import com.intellij.find.editorHeaderActions.NextOccurrenceAction
import com.intellij.find.editorHeaderActions.PrevOccurrenceAction
import com.intellij.find.editorHeaderActions.ToggleMatchCase
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import org.cef.browser.CefBrowser
import javax.swing.JComponent
import javax.swing.text.JTextComponent

internal class MarkdownPreviewSearchSession(
  private val project: Project,
  private val browser: CefBrowser,
  private val targetComponent: JComponent,
) : SearchSession, SearchReplaceComponent.Listener, FindModel.FindModelObserver {
  private val findModel: FindModel = createFindModel()
  private val searchComponent: SearchReplaceComponent = createSearchComponent()

  override fun getFindModel(): FindModel = findModel
  override fun getComponent(): SearchReplaceComponent = searchComponent

  override fun hasMatches(): Boolean = searchComponent.isVisible && searchComponent.searchTextComponent.text.isNotEmpty()

  override fun searchForward() {
    addTextToRecent()
    search(findModel, true)
  }

  override fun searchBackward() {
    addTextToRecent()
    search(findModel, false)
  }

  private fun addTextToRecent() {
    val textComponent = searchComponent.searchTextComponent
    val text = textComponent.text
    if (text.isNotBlank()) {
      searchComponent.addTextToRecent(textComponent)
    }
  }

  private fun search(model: FindModel, forward: Boolean) {
    val stringToFind = model.stringToFind
    if (stringToFind.isNotEmpty()) {
      browser.find(stringToFind, forward, model.isCaseSensitive, true)
    }
  }

  internal fun showSearchBar() {
    searchComponent.isVisible = true
    IdeFocusManager.getInstance(project).requestFocus(searchComponent.searchTextComponent, false)
  }

  override fun close() {
    searchComponent.isVisible = false
    browser.stopFinding(true)
    IdeFocusManager.getInstance(project).requestFocus(targetComponent, false)
  }

  override fun findModelChanged(findModel: FindModel) {
    val stringToFind = findModel.stringToFind

    searchComponent.update(stringToFind, "", false, findModel.isMultiline)

    if (stringToFind.isEmpty()) {
      browser.stopFinding(true)
    }
    else {
      search(findModel, true)
    }
  }

  override fun searchFieldDocumentChanged() {
    findModel.stringToFind = searchComponent.searchTextComponent.text
  }

  private fun createFindModel(): FindModel {
    val model = FindModel()
    model.copyFrom(FindManager.getInstance(project).findInFileModel)
    model.addObserver(this)
    return model
  }

  private fun createSearchComponent(): SearchReplaceComponent {
    val component = SearchReplaceComponent
      .buildFor(project, targetComponent, this)
      .addExtraSearchActions(
        ToggleMatchCase(),
        PrevOccurrenceAction(),
        NextOccurrenceAction(),
      )
      .withMaximizeLeftPanelOnResize()
      .withCloseAction { close() }
      .build()

    component.isVisible = false
    component.addListener(this)
    registerTextComponentActionShortcuts(component.searchTextComponent)

    return component
  }

  private fun registerTextComponentActionShortcuts(component: JTextComponent) {
    val actionManager = ActionManager.getInstance()
    val actions = listOf(
      IdeActions.ACTION_EDITOR_COPY,
      IdeActions.ACTION_EDITOR_CUT,
      IdeActions.ACTION_EDITOR_PASTE,
      IdeActions.ACTION_SELECT_ALL,
      IdeActions.ACTION_UNDO,
      IdeActions.ACTION_REDO,
    ).mapNotNull(actionManager::getAction)

    actions.forEach { it.registerCustomShortcutSet(component, null) }
  }
}
