// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.find.SearchSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.bindApplicationTitle
import com.intellij.ui.util.preferredHeight
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.exp.BlockTerminalController.BlockTerminalControllerListener
import org.jetbrains.plugins.terminal.exp.TerminalPromptController.PromptStateListener
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.*
import javax.swing.JComponent
import javax.swing.JPanel

class BlockTerminalView(
  private val project: Project,
  private val session: BlockTerminalSession,
  private val settings: JBTerminalSystemSettingsProviderBase,
  terminalTitle: TerminalTitle
) : TerminalContentView, TerminalCommandExecutor {
  private val controller: BlockTerminalController
  private val selectionController: TerminalSelectionController
  private val focusModel: TerminalFocusModel

  private val outputView: TerminalOutputView = TerminalOutputView(project, session, settings)
  private val promptView: TerminalPromptView = TerminalPromptView(project, settings, session, this)
  private var alternateBufferView: SimpleTerminalView? = null

  override val component: JComponent = BlockTerminalPanel()

  override val preferredFocusableComponent: JComponent
    get() = when {
      alternateBufferView != null -> alternateBufferView!!.preferredFocusableComponent
      controller.searchSession != null -> controller.searchSession!!.component.searchTextComponent
      promptView.component.isVisible && selectionController.primarySelection == null -> promptView.preferredFocusableComponent
      else -> outputView.preferredFocusableComponent
    }

  init {
    focusModel = TerminalFocusModel(project, this, outputView, promptView)
    selectionController = TerminalSelectionController(focusModel, outputView.controller.selectionModel, outputView.controller.outputModel)
    controller = BlockTerminalController(project, session, outputView.controller, promptView.controller, selectionController, focusModel)

    Disposer.register(this, outputView)
    Disposer.register(this, promptView)

    promptView.controller.addListener(object : PromptStateListener {
      override fun promptVisibilityChanged(visible: Boolean) {
        component.revalidate()
        invokeLater {
          // do not focus the terminal if something outside the terminal is focused now
          // it can be the case when long command is finished and prompt becomes shown
          if (focusModel.isActive) {
            IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
          }
        }
      }
    })

    promptView.controller.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (promptView.component.preferredHeight != promptView.component.height) {
          component.revalidate()
        }
      }
    })

    component.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updateTerminalSize()
      }
    })

    component.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        // move focus to terminal if user clicked in the empty area
        IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
      }
    })

    session.model.addTerminalListener(object : TerminalModel.TerminalListener {
      override fun onAlternateBufferChanged(enabled: Boolean) {
        invokeLater {
          alternateBufferStateChanged(enabled)
        }
      }
    })

    terminalTitle.bindApplicationTitle(session.controller, this)

    controller.addListener(object : BlockTerminalControllerListener {
      override fun searchSessionStarted(session: SearchSession) {
        outputView.installSearchComponent(session.component)
      }

      override fun searchSessionFinished(session: SearchSession) {
        outputView.removeSearchComponent(session.component)
      }
    })

    // Forward key typed events from the output view to the prompt.
    // So, user can start typing even when some block is selected.
    // The focus and the events will just move to the prompt without an explicit focus switch.
    installTypingEventsForwarding(outputView.preferredFocusableComponent, promptView.preferredFocusableComponent)

    installPromptAndOutput()
  }

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    session.controller.resize(initialTermSize, RequestOrigin.User)
    session.start(ttyConnector)
  }

  private fun alternateBufferStateChanged(enabled: Boolean) {
    if (enabled) {
      installAlternateBufferPanel()
    }
    else {
      alternateBufferView?.let { Disposer.dispose(it) }
      alternateBufferView = null
      installPromptAndOutput()
    }
    IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
    invokeLater {
      updateTerminalSize()
    }
  }

  private fun installAlternateBufferPanel() {
    val view = SimpleTerminalView(project, settings, session, withVerticalScroll = false)
    Disposer.register(this, view)
    alternateBufferView = view

    with(component) {
      removeAll()
      add(view.component, BorderLayout.CENTER)
      revalidate()
    }
  }

  private fun installPromptAndOutput() {
    with(component) {
      removeAll()
      add(outputView.component, BorderLayout.CENTER)
      add(promptView.component, BorderLayout.SOUTH)
      revalidate()
    }
  }

  private fun installTypingEventsForwarding(origin: JComponent, destination: JComponent) {
    origin.addKeyListener(object : KeyAdapter() {
      override fun keyTyped(e: KeyEvent) {
        if (e.id == KeyEvent.KEY_TYPED && destination.isShowing) {
          e.consume()
          IdeFocusManager.getInstance(project).requestFocus(destination, true)
          val newEvent = KeyEvent(destination, e.id, e.`when`, e.modifiers, e.keyCode, e.keyChar, e.keyLocation)
          destination.dispatchEvent(newEvent)
        }
      }
    })
  }

  override fun startCommandExecution(command: String) {
    controller.startCommandExecution(command)
  }

  private fun updateTerminalSize() {
    val newSize = getTerminalSize() ?: return
    controller.resize(newSize)
  }

  override fun getTerminalSize(): TermSize? {
    val (width, charSize) = if (alternateBufferView != null) {
      alternateBufferView!!.let { it.terminalWidth to it.charSize }
    }
    else outputView.let { it.terminalWidth to it.charSize }
    return if (width > 0 && component.height > 0) {
      TerminalUiUtils.calculateTerminalSize(Dimension(width, component.height), charSize)
    }
    else null
  }

  override fun isFocused(): Boolean {
    return outputView.component.hasFocus() || promptView.component.hasFocus()
  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    session.addTerminationCallback(onTerminated, parentDisposable)
  }

  override fun sendCommandToExecute(shellCommand: String) {
    controller.startCommandExecution(shellCommand)
  }

  override fun dispose() {}

  private inner class BlockTerminalPanel : JPanel(), DataProvider {
    init {
      background = TerminalUi.terminalBackground
      layout = BorderLayout()
    }

    override fun getData(dataId: String): Any? {
      return when (dataId) {
        TerminalPromptController.KEY.name -> promptView.controller
        TerminalOutputController.KEY.name -> outputView.controller
        SimpleTerminalController.KEY.name -> alternateBufferView?.controller
        BlockTerminalController.KEY.name -> controller
        TerminalSelectionController.KEY.name -> selectionController
        TerminalFocusModel.KEY.name -> focusModel
        BlockTerminalSession.DATA_KEY.name -> session
        else -> null
      }
    }
  }
}