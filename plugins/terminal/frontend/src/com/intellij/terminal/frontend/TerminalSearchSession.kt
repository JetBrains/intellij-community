// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.BundleBase
import com.intellij.find.*
import com.intellij.find.FindModel.FindModelObserver
import com.intellij.find.editorHeaderActions.*
import com.intellij.find.impl.livePreview.LivePreview
import com.intellij.find.impl.livePreview.LivePreviewController
import com.intellij.find.impl.livePreview.LivePreviewPresentation
import com.intellij.find.impl.livePreview.SearchResults
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.BlockTerminalColors
import com.intellij.terminal.actions.TerminalActionUtil
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.util.maximumWidth
import com.intellij.ui.util.preferredWidth
import com.intellij.util.SmartList
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import java.awt.Dimension
import java.awt.Point
import java.util.regex.PatternSyntaxException
import javax.swing.JComponent
import javax.swing.JTextArea

internal class TerminalSearchSession(
  private val project: Project,
  private val editor: Editor,
  private val model: FindModel,
  private val closeCallback: () -> Unit,
) : SearchSession, SearchResults.SearchResultsListener, SearchReplaceComponent.Listener {
  private val disposable = Disposer.newDisposable(TerminalSearchSession::class.java.name)
  private val component: SearchReplaceComponent = createSearchComponent()
  val wrapper: JComponent = DataContextWrapper(component)
  private val searchResults: SearchResults = TerminalSearchResults()
  private val livePreviewController: LivePreviewController = LivePreviewController(searchResults, this, disposable)

  init {
    searchResults.matchesLimit = LivePreviewController.MATCHES_LIMIT
    livePreviewController.on()
    livePreviewController.setLivePreview(LivePreview(searchResults, TerminalSearchPresentation(editor)))

    component.addListener(this)
    searchResults.addListener(this)
    model.addObserver(object : FindModelObserver {
      private var preventRecursion = false

      override fun findModelChanged(findModel: FindModel) {
        if (!preventRecursion) {
          try {
            preventRecursion = true
            findModelChanged()
          }
          finally {
            preventRecursion = false
          }
        }
      }
    })

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
      .buildFor(project, editor.contentComponent, this)
      .addPrimarySearchActions(StatusTextAction(), PrevOccurrenceAction(), NextOccurrenceAction())
      .addExtraSearchActions(ToggleMatchCase(), ToggleRegex())
      .withNewLineButton(false)
      .withCloseAction(this::close)
      .build().also {
        (it.searchTextComponent as? JTextArea)?.columns = 14  // default is 12
        it.border = JBUI.Borders.customLine(JBUI.CurrentTheme.Editor.BORDER_COLOR, 0, 1, 1,0)
      }
  }

  private fun findModelChanged() {
    updateUiWithFindModel()
    searchResults.clear()
    updateResults()
    FindManager.getInstance(project).findInFileModel.apply {
      stringToFind = model.stringToFind
      isCaseSensitive = model.isCaseSensitive
      isRegularExpressions = model.isRegularExpressions
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
    if (matchesCount == 0) {
      component.setNotFoundBackground()
    }
    else {
      component.setRegularBackground()
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
      if (model.isRegularExpressions) {
        checkRegex(text)?.let { warning ->
          searchResults.clear()
          component.statusText = warning
          return
        }
      }
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

  /**
   * Returns warning string if regex is incorrect, null otherwise.
   * Partially copied from [EditorSearchSession.updateResults].
   */
  private fun checkRegex(text: String): @Nls String? {
    try {
      Regex(text)
    }
    catch (e: PatternSyntaxException) {
      return FindBundle.message(SearchSession.INCORRECT_REGEXP_MESSAGE_KEY)
    }
    return if (text.matches(Regex("\\|+"))) {
      ApplicationBundle.message("editorsearch.empty.string.matches")
    }
    else null
  }

  fun activate() {
    component.requestFocusInTheSearchFieldAndSelectContent(project)
    FindUtil.configureFindModel(false, editor, findModel, false)
    findModel.isGlobal = false
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
    // We only need to transfer the focus if the editor is still visible.
    // There can be several reasons for closing the search,
    // and in some cases the editor can be hidden (e.g., switching to the alternate buffer).
    if (editor.contentComponent.isShowing) {
      editor.contentComponent.requestFocusInWindow()
    }
  }

  private class TerminalSearchPresentation(private val editor: Editor) : LivePreviewPresentation {
    override val defaultAttributes: TextAttributes
      get() = editor.colorsScheme.getAttributes(BlockTerminalColors.SEARCH_ENTRY) ?: TextAttributes()
    override val cursorAttributes: TextAttributes
      get() = editor.colorsScheme.getAttributes(BlockTerminalColors.CURRENT_SEARCH_ENTRY) ?: TextAttributes()

    override val defaultLayer: Int = HighlighterLayer.SELECTION + 1
    override val cursorLayer: Int = HighlighterLayer.SELECTION + 2
  }

  private inner class TerminalSearchResults : SearchResults(editor, project) {
    override fun getLocalSearchArea(editor: Editor, findModel: FindModel): SearchArea {
      return SearchArea.create(intArrayOf(0), intArrayOf(Int.MAX_VALUE))
    }

    /**
     * Select the first occurence in the visible area.
     * If there are no occurrences visible, the first occurrence below is selected.
     * If there are no occurrences below, the first occurence above is selected.
     */
    override fun firstOccurrenceAtOrAfterCaret(): FindResult? {
      val topY = editor.scrollingModel.visibleArea.y + 3 * editor.lineHeight
      val topLogicalPosition = editor.xyToLogicalPosition(Point(0, topY))
      val topOffset = editor.logicalPositionToOffset(topLogicalPosition)
      val index = occurrences.indexOfFirst { it.startOffset >= topOffset }
      return if (index > 0) {
        occurrences[index]
      }
      else occurrences.lastOrNull()
    }
  }
}

private class DataContextWrapper(component: JComponent): Wrapper(component), UiDataProvider {
  override fun uiDataSnapshot(sink: DataSink) {
    sink.setNull(TerminalActionUtil.EDITOR_KEY) // disable editor actions when the search is in focus
  }

  override fun getPreferredSize(): Dimension = super.getPreferredSize().also {
    it.width = JBUI.scale(TerminalUi.searchComponentWidth)
  }

  override fun getMaximumSize(): Dimension = super.getMaximumSize().also {
    it.width = JBUI.scale(TerminalUi.searchComponentWidth)
  }
}
