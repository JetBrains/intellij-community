// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.jcef

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.SearchReplaceComponent
import com.intellij.find.SearchSession
import com.intellij.find.editorHeaderActions.ToggleMatchCase
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import org.cef.browser.CefBrowser
import javax.swing.JComponent

internal class MarkdownPreviewSearchSession(
  private val project: Project,
  private val browser: CefBrowser,
  private val targetComponent: JComponent,
) : SearchSession, SearchReplaceComponent.Listener, FindModel.FindModelObserver {
  private val findModel: FindModel = createFindModel()
  private val searchComponent: SearchReplaceComponent = createSearchComponent()

  override fun getFindModel(): FindModel = findModel
  override fun getComponent(): SearchReplaceComponent = searchComponent

  override fun hasMatches(): Boolean = false
  override fun searchForward() {}
  override fun searchBackward() {}

  internal fun showSearchBar() {
    searchComponent.isVisible = true
    IdeFocusManager.getInstance(project).requestFocus(searchComponent.searchTextComponent, false)
  }

  override fun close() {
    searchComponent.isVisible = false
    searchComponent.searchTextComponent.text = ""
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
      browser.find(stringToFind, true, findModel.isCaseSensitive, false)
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
      .addExtraSearchActions(ToggleMatchCase())
      .withShowOnlySearchPanel()
      .withCloseAction { close() }
      .build()

    component.isVisible = false
    component.addListener(this)

    return component
  }
}
