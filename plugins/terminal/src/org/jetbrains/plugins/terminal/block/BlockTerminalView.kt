// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.find.SearchSession
import com.intellij.ide.GeneralSettings
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.bindApplicationTitle
import com.intellij.ui.util.preferredHeight
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBInsets
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.action.TerminalInterruptCommandAction
import org.jetbrains.plugins.terminal.action.TerminalMoveCaretToLineEndAction
import org.jetbrains.plugins.terminal.action.TerminalMoveCaretToLineStartAction
import org.jetbrains.plugins.terminal.block.BlockTerminalController.BlockTerminalControllerListener
import org.jetbrains.plugins.terminal.block.output.TerminalOutputController
import org.jetbrains.plugins.terminal.block.output.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.output.TerminalOutputView
import org.jetbrains.plugins.terminal.block.output.TerminalSelectionController
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptController
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptController.PromptStateListener
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptView
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.session.CommandFinishedEvent
import org.jetbrains.plugins.terminal.block.session.ShellCommandListener
import org.jetbrains.plugins.terminal.block.session.TerminalModel
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils.getComponentSizeInitializedFuture
import org.jetbrains.plugins.terminal.block.ui.getDisposed
import org.jetbrains.plugins.terminal.block.ui.invokeLater
import org.jetbrains.plugins.terminal.util.ShellType
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.*
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

internal class BlockTerminalView(
  private val project: Project,
  private val session: BlockTerminalSession,
  private val settings: JBTerminalSystemSettingsProviderBase,
  terminalTitle: TerminalTitle
) : TerminalContentView, TerminalCommandExecutor {
  private val controller: BlockTerminalController
  private val selectionController: TerminalSelectionController
  private val focusModel: TerminalFocusModel = TerminalFocusModel(project, this)

  val outputView: TerminalOutputView = TerminalOutputView(project, session, settings, focusModel)
  val promptView: TerminalPromptView = TerminalPromptView(project, settings, session, this)
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
    selectionController = TerminalSelectionController(focusModel, outputView.controller.selectionModel, outputView.controller.outputModel)
    controller = BlockTerminalController(project, session, outputView.controller, promptView.controller, selectionController, focusModel)

    Disposer.register(this, outputView)
    Disposer.register(this, promptView)

    promptView.controller.addListener(object : PromptStateListener {
      override fun promptVisibilityChanged(visible: Boolean) {
        val wasActive = focusModel.isActive
        promptView.component.isVisible = visible
        component.revalidate()
        if (wasActive) {
          IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
        }
      }
    })
    promptView.controller.promptIsVisible = false

    promptView.controller.model.editor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (promptView.component.preferredHeight != promptView.component.height) {
          component.revalidate()
        }
      }
    })

    outputView.controller.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        component.revalidate()
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
        invokeLater(getDisposed(), ModalityState.any()) {
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

    installActions()

    focusModel.addListener(object: TerminalFocusModel.TerminalFocusListener {
      override fun activeStateChanged(isActive: Boolean) {
        if (isActive) {
          if (GeneralSettings.getInstance().isSaveOnFrameDeactivation) {
            WriteIntentReadAction.run {
              FileDocumentManager.getInstance().saveAllDocuments()
            }
          }
        }
        else {
          // refresh when running a long-running command and switching outside the terminal
          SaveAndSyncHandler.getInstance().scheduleRefresh()
        }
      }
    })
    session.commandManager.addListener(object: ShellCommandListener {
      override fun commandFinished(event: CommandFinishedEvent) {
        SaveAndSyncHandler.getInstance().scheduleRefresh()
      }
    }, this)
  }

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    session.controller.resize(initialTermSize, RequestOrigin.User)
    session.start(ttyConnector)
  }

  @RequiresEdt(generateAssertion = false)
  private fun alternateBufferStateChanged(enabled: Boolean) {
    if (enabled) {
      installAlternateBufferPanel()
    }
    else {
      alternateBufferView?.let { Disposer.dispose(it) }
      alternateBufferView = null
      installPromptAndOutput()
    }
    outputView.controller.alternateBufferStateChanged(enabled)
    IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
    invokeLater(getDisposed(), ModalityState.any()) {
      updateTerminalSize()
    }
  }

  private fun installAlternateBufferPanel() {
    val view = SimpleTerminalView(project, settings, session, withVerticalScroll = false)
    Disposer.register(this, view)
    alternateBufferView = view

    with(component) {
      removeAll()
      add(view.component)
      revalidate()
    }
  }

  private fun installPromptAndOutput() {
    with(component) {
      removeAll()
      add(outputView.component)
      add(promptView.component)
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

  // todo: Would be great to have a separate lists of actions for each shell
  //  in something like TerminalShellSupport, to get them from the method instead of using if's.
  private fun installActions() {
    TerminalInterruptCommandAction().registerCustomShortcutSet(component, null)
    if (session.shellIntegration.shellType != ShellType.POWERSHELL) {
      // Do not add custom actions for moving the caret in PowerShell because Home and End shortcuts are used there.
      // But Home and End are already handled by default editor action implementations.
      listOf(
        TerminalMoveCaretToLineStartAction(),
        TerminalMoveCaretToLineEndAction()
      ).forEach {
        it.registerCustomShortcutSet(component, null)
      }
    }
  }

  override fun startCommandExecution(command: String) {
    controller.startCommandExecution(command)
  }

  private fun updateTerminalSize() {
    if (getTerminalSizeInitializedFuture().isDone) {
      val newSize = getTerminalSize() ?: return
      controller.resize(newSize)
    }
  }

  override fun getTerminalSize(): TermSize? {
    val (width, charSize) = if (alternateBufferView != null) {
      alternateBufferView!!.let { it.terminalWidth to it.charSize }
    }
    else {
      // Use the width of the prompt as a target, because it can be reduced by the side action toolbar.
      // Need to take the reduced width to not intersect with the toolbar.
      promptView.let { it.terminalWidth to it.charSize }
    }
    return if (width > 0 && component.height > 0) {
      TerminalUiUtils.calculateTerminalSize(Dimension(width, component.height), charSize)
    }
    else null
  }

  override fun getTerminalSizeInitializedFuture(): CompletableFuture<*> {
    // Wait for terminal component size initialization to get the correct terminal height
    val componentSizeInitializedFuture = getComponentSizeInitializedFuture(component)
    val terminalWidthInitializedFuture = promptView.getTerminalWidthInitializedFuture()
    return CompletableFuture.allOf(componentSizeInitializedFuture, terminalWidthInitializedFuture)
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

  private fun getDisposed(): () -> Boolean = outputView.controller.outputModel.editor.getDisposed()

  private inner class BlockTerminalPanel : JPanel(), UiDataProvider {
    init {
      background = TerminalUi.defaultBackground(outputView.controller.outputModel.editor)
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[TerminalPromptController.KEY] = promptView.controller
      sink[TerminalOutputController.KEY] = outputView.controller
      sink[TerminalOutputModel.KEY] = outputView.controller.outputModel
      sink[SimpleTerminalController.KEY] = alternateBufferView?.controller
      sink[BlockTerminalController.KEY] = controller
      sink[TerminalSelectionController.KEY] = selectionController
      sink[TerminalFocusModel.KEY] = focusModel
      sink[BlockTerminalSession.DATA_KEY] = session
    }

    override fun doLayout() {
      val rect = bounds
      JBInsets.removeFrom(rect, insets)
      when (componentCount) {
        1 -> {
          // it is an alternate buffer editor
          val component = getComponent(0)
          component.bounds = rect
        }
        2 -> layoutPromptAndOutput(rect)
        else -> error("Maximum 2 components expected")
      }
    }

    /**
     * Place output at the top and the prompt (bottomComponent) below it.
     * Always honor the preferred height of the prompt, decrease the height of the output in favor of prompt.
     * So, initially, when output is empty, the prompt is on the top.
     * But when there is a long output, the prompt is stick to the bottom.
     */
    private fun layoutPromptAndOutput(rect: Rectangle) {
      val topComponent = getComponent(0)
      val bottomComponent = getComponent(1)
      val topPrefSize = if (topComponent.isVisible) topComponent.preferredSize else Dimension()
      val bottomPrefSize = if (bottomComponent.isVisible) bottomComponent.preferredSize else Dimension()

      val bottomHeight = max(rect.height - topPrefSize.height, bottomPrefSize.height)
      val topHeight = rect.height - bottomHeight
      topComponent.bounds = Rectangle(rect.x, rect.y, rect.width, topHeight)
      bottomComponent.bounds = Rectangle(rect.x, rect.y + topHeight, rect.width, bottomHeight)
    }
  }
}
