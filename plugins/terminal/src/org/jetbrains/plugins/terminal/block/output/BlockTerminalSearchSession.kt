// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.BundleBase
import com.intellij.find.*
import com.intellij.find.FindModel.FindModelObserver
import com.intellij.find.editorHeaderActions.*
import com.intellij.find.impl.livePreview.LivePreview
import com.intellij.find.impl.livePreview.LivePreviewController
import com.intellij.find.impl.livePreview.LivePreviewPresentation
import com.intellij.find.impl.livePreview.SearchResults
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.BlockTerminalColors
import com.intellij.ui.util.maximumWidth
import com.intellij.ui.util.preferredWidth
import com.intellij.util.SmartList
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalIcons
import org.jetbrains.plugins.terminal.block.output.TerminalSelectionModel.TerminalSelectionListener
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import java.awt.Point
import java.util.regex.PatternSyntaxException
import javax.swing.JTextArea

internal class BlockTerminalSearchSession(
  private val project: Project,
  private val editor: EditorEx,
  private val model: FindModel,
  private val outputModel: TerminalOutputModel,
  private val selectionModel: TerminalSelectionModel,
  private val closeCallback: () -> Unit = {}
) : SearchSession, SearchResults.SearchResultsListener, SearchReplaceComponent.Listener {
  private val disposable = Disposer.newDisposable(BlockTerminalSearchSession::class.java.name)
  private val component: SearchReplaceComponent = createSearchComponent()
  private val searchResults: SearchResults = TerminalSearchResults()
  private val livePreviewController: LivePreviewController = LivePreviewController(searchResults, this, disposable)
  private var isSearchInBlock = model.isSearchInBlock

  init {
    searchResults.matchesLimit = LivePreviewController.MATCHES_LIMIT
    livePreviewController.on()
    livePreviewController.setLivePreview(LivePreview(searchResults, TerminalSearchPresentation(editor)))

    component.addListener(this)
    searchResults.addListener(this)
    model.addObserver(object : FindModelObserver {
      private var preventRecursion = false

      override fun findModelChanged(findModel: FindModel?) {
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
    selectionModel.addListener(object : TerminalSelectionListener {
      override fun selectionChanged(oldSelection: List<CommandBlock>, newSelection: List<CommandBlock>) {
        model.isSearchInBlock = newSelection.isNotEmpty()
        searchResults.clear()
        updateResults()
      }
    }, disposable)

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
      .addExtraSearchActions(SearchInBlockAction(), ToggleMatchCase(), ToggleRegex())
      .withNewLineButton(false)
      .withCloseAction(this::close)
      .build().also {
        (it.searchTextComponent as? JTextArea)?.columns = 14  // default is 12
        it.preferredWidth = JBUI.scale(TerminalUi.searchComponentWidth)
        it.maximumWidth = JBUI.scale(TerminalUi.searchComponentWidth)
        it.border = JBUI.Borders.customLine(JBUI.CurrentTheme.Editor.BORDER_COLOR, 0, 1, 1,0)
      }
  }

  private fun findModelChanged() {
    if (model.isSearchInBlock != isSearchInBlock) {
      isSearchInBlock = model.isSearchInBlock
      if (isSearchInBlock && selectionModel.primarySelection == null) {
        val offset = searchResults.cursor?.startOffset ?: editor.caretModel.offset
        outputModel.getByOffset(offset)?.let { block ->
          selectionModel.selectedBlocks = listOf(block)
        }
      }
      else if (!isSearchInBlock) {
        selectionModel.selectedBlocks = emptyList()
      }
    }
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
    if (model.isSearchInBlock) {
      return TerminalBundle.message("search.in.block").replace(BundleBase.MNEMONIC_STRING, "")
    }
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
      return if (findModel.isSearchInBlock) {
        val blocks = selectionModel.selectedBlocks.sortedBy { it.startOffset }
        val starts = IntArray(blocks.size)
        val ends = IntArray(blocks.size)
        for (index in blocks.indices) {
          starts[index] = blocks[index].startOffset
          ends[index] = blocks[index].endOffset
        }
        SearchArea.create(starts, ends)
      }
      else SearchArea.create(intArrayOf(0), intArrayOf(Int.MAX_VALUE))
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

  private class SearchInBlockAction : Embeddable, TooltipDescriptionProvider,
                                      EditorHeaderToggleAction(TerminalBundle.message("search.in.block"),
                                                               TerminalIcons.SearchInBlock,
                                                               TerminalIcons.SearchInBlock,
                                                               TerminalIcons.SearchInBlock) {
    override fun isSelected(session: SearchSession): Boolean {
      return session.findModel.isSearchInBlock
    }

    override fun setSelected(session: SearchSession, selected: Boolean) {
      session.findModel.isSearchInBlock = selected
    }
  }

  companion object {
    private val SEARCH_IN_BLOCK_KEY: Key<Boolean> = Key.create("SearchInBlock")

    var FindModel.isSearchInBlock: Boolean
      get() = getCopyableUserData(SEARCH_IN_BLOCK_KEY) == true
      set(value) {
        putCopyableUserData(SEARCH_IN_BLOCK_KEY, value)
      }
  }
}
