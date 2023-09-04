// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.BundleBase
import com.intellij.find.FindBundle
import com.intellij.find.FindModel
import com.intellij.find.SearchReplaceComponent
import com.intellij.find.SearchSession
import com.intellij.find.editorHeaderActions.*
import com.intellij.find.impl.livePreview.LivePreviewController
import com.intellij.find.impl.livePreview.SearchResults
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.util.maximumWidth
import com.intellij.ui.util.preferredWidth
import com.intellij.util.SmartList
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.JTextArea

class BlockTerminalSearchSession(
  private val project: Project,
  private val editor: EditorEx,
  private val model: FindModel,
  private val closeCallback: () -> Unit = {}
) : SearchSession, SearchResults.SearchResultsListener, SearchReplaceComponent.Listener, DataProvider {
  private val disposable = Disposer.newDisposable(BlockTerminalSearchSession::class.java.name)
  private val component: SearchReplaceComponent = createSearchComponent()
  private val searchResults: SearchResults = SearchResults(editor, project)
  private val livePreviewController: LivePreviewController = LivePreviewController(searchResults, this, disposable)

  init {
    searchResults.matchesLimit = LivePreviewController.MATCHES_LIMIT
    livePreviewController.on()

    component.addListener(this)
    searchResults.addListener(this)
    model.addObserver {
      updateUiWithFindModel()
      searchResults.clear()
      updateResults()
    }
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorReleased(event: EditorFactoryEvent) {
        if (event.editor === editor) {
          Disposer.dispose(disposable)
          livePreviewController.dispose()
        }
      }
    }, disposable)

    component.statusText = ApplicationBundle.message("editorsearch.current.cursor.position", 0, 0)
    updateUiWithFindModel()
    updateMultiLineStateIfNeeded()
    invokeLater {  // update status text action
      component.updateActions()
    }
  }

  private fun createSearchComponent(): SearchReplaceComponent {
    return SearchReplaceComponent
      .buildFor(project, editor.contentComponent)
      .addPrimarySearchActions(StatusTextAction(), PrevOccurrenceAction(), NextOccurrenceAction())
      .addExtraSearchActions(ToggleMatchCase(), ToggleRegex())
      .withNewLineButton(false)
      .withDataProvider(this)
      .withCloseAction(this::close)
      .build().also {
        (it.searchTextComponent as? JTextArea)?.columns = 14  // default is 12
        it.preferredWidth = JBUI.scale(TerminalUi.searchComponentWidth)
        it.maximumWidth = JBUI.scale(TerminalUi.searchComponentWidth)
        it.border = JBUI.Borders.customLine(JBUI.CurrentTheme.Editor.BORDER_COLOR, 0, 1, 1,0)
      }
  }

  override fun searchFieldDocumentChanged() {
    if (editor.isDisposed) return
    model.stringToFind = component.searchTextComponent.text
    updateResults()
    updateMultiLineStateIfNeeded()
  }

  override fun searchResultsUpdated(sr: SearchResults) {
    if (sr.findModel == null) {
      return
    }
    val matchesCount = sr.matchesCount
    val cursorIndex = sr.getCursorVisualIndex()
    val status = when {
      matchesCount > searchResults.matchesLimit -> ApplicationBundle.message("editorsearch.toomuch", searchResults.matchesLimit)
      cursorIndex != -1 -> ApplicationBundle.message("editorsearch.current.cursor.position", cursorIndex, matchesCount)
      else -> ApplicationBundle.message("editorsearch.current.cursor.position", 0, matchesCount)
    }
    component.statusText = status
    component.updateActions()
  }

  override fun cursorMoved() {
    component.updateActions()
  }

  private fun updateResults() {
    val text = model.stringToFind
    if (text.isNotEmpty()) {
      livePreviewController.updateInBackground(model, true)
    }
    else {
      component.statusText = ApplicationBundle.message("editorsearch.current.cursor.position", 0, 0)
      searchResults.clear()
    }
  }

  private fun updateMultiLineStateIfNeeded() {
    model.isMultiline = component.searchTextComponent.text.contains("\n")
  }

  private fun updateUiWithFindModel() {
    component.update(model.stringToFind, model.stringToReplace, model.isReplaceState, model.isMultiline)
    updateEmptyText()
    livePreviewController.setTrackingSelection(!model.isGlobal)
  }

  private fun updateEmptyText() {
    val searchComponent = component.searchTextComponent
    if (searchComponent is ComponentWithEmptyText) {
      searchComponent.emptyText.text = getEmptyText()
    }
  }

  private fun getEmptyText(): @Nls String {
    fun getOptionText(key: String) = StringUtil.toLowerCase(FindBundle.message(key).replace(BundleBase.MNEMONIC_STRING, ""))
    val options: MutableList<String> = SmartList()
    if (model.isCaseSensitive) options.add(getOptionText("find.case.sensitive"))
    if (model.isRegularExpressions) options.add(getOptionText("find.regex"))
    val text = when (options.size) {
      0 -> ApplicationBundle.message("editorsearch.search.hint")
      1 -> FindBundle.message("emptyText.used.option", options[0])
      else -> FindBundle.message("emptyText.used.options", options[0], options[1])
    }
    return StringUtil.capitalize(text)
  }

  override fun getFindModel(): FindModel = model

  override fun getComponent(): SearchReplaceComponent = component

  override fun hasMatches(): Boolean = searchResults.hasMatches()

  override fun searchForward() {
    livePreviewController.moveCursor(SearchResults.Direction.DOWN)
  }

  override fun searchBackward() {
    livePreviewController.moveCursor(SearchResults.Direction.UP)
  }

  override fun close() {
    Disposer.dispose(disposable)
    livePreviewController.dispose()
    closeCallback()
  }

  override fun getData(dataId: String): Any? {
    return if (SearchSession.KEY.`is`(dataId)) this else null
  }
}