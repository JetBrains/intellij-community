// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.find.FindModel
import com.intellij.find.SearchReplaceComponent
import com.intellij.find.SearchSession
import com.intellij.find.editorHeaderActions.NextOccurrenceAction
import com.intellij.find.editorHeaderActions.PrevOccurrenceAction
import com.intellij.find.editorHeaderActions.StatusTextAction
import com.intellij.find.editorHeaderActions.ToggleMatchCase
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalWidget
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jediterm.terminal.SubstringFinder
import com.jediterm.terminal.ui.JediTermWidget
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

internal class TerminalSearchSession(private val terminalWidget: JBTerminalWidget) : SearchSession, DataProvider {
  private val searchComponent: SearchReplaceComponent = createSearchComponent()
  private val findModel: FindModel = createFindModel()
  private var hasMatches: Boolean = false
  private val terminalSearchComponent: MySearchComponent = MySearchComponent()

  private val searchComponentWrapper: BorderLayoutPanel

  init {
    searchComponentWrapper = object : BorderLayoutPanel() {
      override fun requestFocus() {
        IdeFocusManager.getInstance(terminalWidget.project).requestFocus(searchComponent.searchTextComponent, false)
      }
    }
    searchComponentWrapper.addToCenter(searchComponent)
    searchComponentWrapper.border = JBUI.Borders.customLine(JBUI.CurrentTheme.Editor.BORDER_COLOR, 0, 1, 0, 1)
  }

  fun getTerminalSearchComponent(): JediTermWidget.SearchComponent = terminalSearchComponent

  private fun createFindModel(): FindModel {
    return FindModel().also {
      it.addObserver {
        terminalSearchComponent.fireSettingsChanged()
      }
    }
  }

  override fun getFindModel(): FindModel = findModel

  override fun getComponent(): SearchReplaceComponent = searchComponent

  override fun hasMatches(): Boolean = hasMatches

  override fun searchForward() {
    terminalSearchComponent.fireKeyPressedEvent(KeyEvent.VK_UP)
  }

  override fun searchBackward() {
    terminalSearchComponent.fireKeyPressedEvent(KeyEvent.VK_DOWN)
  }

  override fun close() {
    terminalSearchComponent.fireKeyPressedEvent(KeyEvent.VK_ESCAPE)
    IdeFocusManager.getInstance(terminalWidget.project).requestFocus(terminalWidget.terminalPanel, false)
  }

  override fun getData(dataId: String): Any? {
    return if (SearchSession.KEY.`is`(dataId)) this else null
  }

  private fun createSearchComponent(): SearchReplaceComponent {
    return SearchReplaceComponent
      .buildFor(terminalWidget.project, terminalWidget.terminalPanel)
      .addExtraSearchActions(ToggleMatchCase())
      .addPrimarySearchActions(StatusTextAction(), PrevOccurrenceAction(), NextOccurrenceAction())
      .withCloseAction { close() }
      .withDataProvider(this)
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

  private inner class MySearchComponent : JediTermWidget.SearchComponent {
    private val settingsChangedListeners: CopyOnWriteArrayList<Runnable> = CopyOnWriteArrayList()
    private val keyListeners: CopyOnWriteArrayList<KeyListener> = CopyOnWriteArrayList()

    override fun getText(): String = findModel.stringToFind

    override fun ignoreCase(): Boolean = !findModel.isCaseSensitive

    override fun getComponent(): JComponent = searchComponentWrapper

    override fun addSettingsChangedListener(onChangeListener: Runnable) {
      settingsChangedListeners.add(onChangeListener)
    }

    override fun addKeyListener(listener: KeyListener) {
      searchComponent.searchTextComponent.addKeyListener(listener)
      keyListeners.add(listener)
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

    fun fireKeyPressedEvent(keyCode: Int) {
      for (keyListener in keyListeners) {
        keyListener.keyPressed(KeyEvent(searchComponent.searchTextComponent, 0, System.currentTimeMillis(), 0, keyCode, ' '))
      }
    }

    fun fireSettingsChanged() {
      for (settingsChangedListener in settingsChangedListeners) {
        settingsChangedListener.run()
      }
    }
  }
}
