// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.search

import com.intellij.find.*
import com.intellij.find.editorHeaderActions.NextOccurrenceAction
import com.intellij.find.editorHeaderActions.PrevOccurrenceAction
import com.intellij.find.editorHeaderActions.StatusTextAction
import com.intellij.find.editorHeaderActions.ToggleMatchCase
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalWidget
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jediterm.terminal.SubstringFinder
import com.jediterm.terminal.ui.JediTermSearchComponent
import com.jediterm.terminal.ui.JediTermSearchComponentListener
import java.awt.event.KeyListener
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

internal class TerminalSearchSession(private val terminalWidget: JBTerminalWidget) : SearchSession {
  private val searchComponent: SearchReplaceComponent = createSearchComponent()
  private val findModel: FindModel = createFindModel()
  private var hasMatches: Boolean = false
  private val terminalSearchComponent: MySearchComponent = MySearchComponent()

  private val searchComponentWrapper: BorderLayoutPanel

  private val project
    get() = terminalWidget.project

  init {
    searchComponentWrapper = object : BorderLayoutPanel() {
      override fun requestFocus() {
        IdeFocusManager.getInstance(project).requestFocus(searchComponent.searchTextComponent, false)
      }
    }
    searchComponentWrapper.addToCenter(searchComponent)
    searchComponentWrapper.border = JBUI.Borders.customLine(JBUI.CurrentTheme.Editor.BORDER_COLOR, 0, 1, 0, 1)
    val selectedText = terminalWidget.selectedText
    if (selectedText != null) {
      searchComponent.searchTextComponent.text = selectedText
      searchComponent.searchTextComponent.selectAll()
    }
  }

  fun getTerminalSearchComponent(): JediTermSearchComponent = terminalSearchComponent

  private fun createFindModel(): FindModel {
    return FindModel().also { findModel ->
      findModel.copyFrom(FindManager.getInstance(project).findInFileModel)
      findModel.addObserver {
        terminalSearchComponent.eventMulticaster.searchSettingsChanged(findModel.stringToFind, !findModel.isCaseSensitive)
        FindUtil.updateFindInFileModel(project, findModel, false)
      }
    }
  }

  override fun getFindModel(): FindModel = findModel

  override fun getComponent(): SearchReplaceComponent = searchComponent

  override fun hasMatches(): Boolean = hasMatches

  override fun searchForward() {
    terminalSearchComponent.eventMulticaster.selectNextFindResult()
  }

  override fun searchBackward() {
    terminalSearchComponent.eventMulticaster.selectPrevFindResult()
  }

  override fun close() {
    terminalSearchComponent.eventMulticaster.hideSearchComponent()
    IdeFocusManager.getInstance(project).requestFocus(terminalWidget.terminalPanel, false)
  }

  private fun createSearchComponent(): SearchReplaceComponent {
    return SearchReplaceComponent
      .buildFor(project, terminalWidget.terminalPanel, this)
      .addExtraSearchActions(ToggleMatchCase())
      .addPrimarySearchActions(StatusTextAction(), PrevOccurrenceAction(), NextOccurrenceAction())
      .withCloseAction { close() }
      .build().also {
        it.addListener(object : SearchReplaceComponent.Listener {
          override fun searchFieldDocumentChanged() {
            findModel.stringToFind = searchComponent.searchTextComponent.text
          }

          override fun replaceFieldDocumentChanged() {}

          override fun multilineStateChanged() {}
        })
      }
  }

  private inner class MySearchComponent : JediTermSearchComponent {
    private val listeners: MutableList<JediTermSearchComponentListener> = CopyOnWriteArrayList()

    override fun getComponent(): JComponent = searchComponentWrapper

    override fun addListener(listener: JediTermSearchComponentListener) {
      listeners.add(listener)
    }

    override fun addKeyListener(listener: KeyListener) {
      searchComponent.searchTextComponent.addKeyListener(listener)
    }

    override fun onResultUpdated(results: SubstringFinder.FindResult?) {
      hasMatches = results != null && results.items.isNotEmpty()
      if (results == null) {
        searchComponent.setRegularBackground()
        searchComponent.statusText = ""
      }
      else {
        if (results.items.isEmpty()) {
          searchComponent.setNotFoundBackground()
          searchComponent.statusText = ApplicationBundle.message("editorsearch.matches", results.items.size)
        }
        else {
          searchComponent.setRegularBackground()
          searchComponent.statusText = ApplicationBundle.message("editorsearch.current.cursor.position",
                                                                 results.selectedItem().index, results.items.size)
        }
      }
    }

    val eventMulticaster: JediTermSearchComponentListener = object: JediTermSearchComponentListener {
      override fun searchSettingsChanged(textToFind: String, ignoreCase: Boolean) {
        for (listener in listeners) {
          listener.searchSettingsChanged(textToFind, ignoreCase)
        }
      }

      override fun hideSearchComponent() {
        for (listener in listeners) {
          listener.hideSearchComponent()
        }
      }

      override fun selectNextFindResult() {
        for (listener in listeners) {
          listener.selectNextFindResult()
        }
      }

      override fun selectPrevFindResult() {
        for (listener in listeners) {
          listener.selectPrevFindResult()
        }
      }
    }
  }
}
