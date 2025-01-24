// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.SoftWrapModelImpl
import com.intellij.openapi.editor.impl.softwrap.EmptySoftWrapPainter
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.LocalTimeCounter
import com.intellij.util.asDisposable
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.TerminalContentView
import org.jetbrains.plugins.terminal.block.output.NEW_TERMINAL_OUTPUT_CAPACITY_KB
import org.jetbrains.plugins.terminal.block.output.TerminalOutputEditorInputMethodSupport
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalOutputFileType
import org.jetbrains.plugins.terminal.block.reworked.session.*
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import org.jetbrains.plugins.terminal.block.ui.TerminalUi.useTerminalDefaultBackground
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.VerticalSpaceInlayRenderer
import org.jetbrains.plugins.terminal.block.ui.calculateTerminalSize
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JScrollPane

internal class ReworkedTerminalView(
  private val project: Project,
  private val settings: JBTerminalSystemSettingsProviderBase,
) : TerminalContentView {
  private val coroutineScope = terminalProjectScope(project).childScope("ReworkedTerminalView")
  private val terminalSessionFuture = CompletableFuture<TerminalSession>()

  private val terminationListeners: MutableList<Runnable> = CopyOnWriteArrayList()

  private val sessionModel: TerminalSessionModel
  private val encodingManager: TerminalKeyEncodingManager
  private val controller: TerminalSessionController

  private val terminalInput: TerminalInput

  private val outputModel: TerminalOutputModel
  private val alternateBufferModel: TerminalOutputModel

  private val terminalPanel: TerminalPanel

  override val component: JComponent
    get() = terminalPanel
  override val preferredFocusableComponent: JComponent
    get() = terminalPanel.preferredFocusableComponent

  init {
    Disposer.register(this) {
      // Complete to avoid memory leaks with hanging callbacks. If already completed, nothing will change.
      terminalSessionFuture.complete(null)

      coroutineScope.cancel()
    }

    coroutineScope.coroutineContext.job.invokeOnCompletion {
      for (listener in terminationListeners) {
        try {
          listener.run()
        }
        catch (t: Throwable) {
          thisLogger().error("Unhandled exception in termination listener", t)
        }
      }
    }

    sessionModel = TerminalSessionModelImpl(settings)
    encodingManager = TerminalKeyEncodingManager(sessionModel, coroutineScope.childScope("TerminalKeyEncodingManager"))

    terminalInput = TerminalInput(terminalSessionFuture, sessionModel)

    outputModel = createOutputModel(
      editor = createOutputEditor(settings, parentDisposable = this),
      maxOutputLength = AdvancedSettings.getInt(NEW_TERMINAL_OUTPUT_CAPACITY_KB).coerceIn(1, 10 * 1024) * 1024,
      settings,
      sessionModel,
      encodingManager,
      terminalInput,
      coroutineScope.childScope("TerminalOutputModel"),
      withVerticalScroll = true,
      withTopAndBottomInsets = true
    )

    alternateBufferModel = createOutputModel(
      editor = createAlternateBufferEditor(settings, parentDisposable = this),
      maxOutputLength = 0,
      settings,
      sessionModel,
      encodingManager,
      terminalInput,
      coroutineScope.childScope("TerminalAlternateBufferModel"),
      withVerticalScroll = false,
      withTopAndBottomInsets = false
    )

    val blocksModel = TerminalBlocksModelImpl(outputModel)
    TerminalBlocksDecorator(outputModel, blocksModel, coroutineScope.childScope("TerminalBlocksDecorator"))

    controller = TerminalSessionController(
      sessionModel,
      outputModel,
      alternateBufferModel,
      blocksModel,
      settings,
      coroutineScope.childScope("TerminalSessionController")
    )

    terminalPanel = TerminalPanel(initialContent = outputModel.editor)

    (outputModel.editor.softWrapModel as? SoftWrapModelImpl)?.setSoftWrapPainter(EmptySoftWrapPainter)
    listenPanelSizeChanges()
    listenAlternateBufferSwitch()
  }

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    val session = startTerminalSession(ttyConnector, initialTermSize, settings, coroutineScope.childScope("TerminalSession"))
    terminalSessionFuture.complete(session)

    controller.handleEvents(session.outputChannel)
  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    TerminalUtil.addItem(terminationListeners, onTerminated, parentDisposable)
  }

  override fun sendCommandToExecute(shellCommand: String) {
    terminalSessionFuture.thenAccept {
      val newLineBytes = encodingManager.getCode(KeyEvent.VK_ENTER, 0)!!
      // TODO: should we always use UTF8?
      val bytes = shellCommand.toByteArray(Charsets.UTF_8) + newLineBytes
      it?.inputChannel?.trySend(TerminalWriteBytesEvent(bytes))
    }
  }

  override fun getTerminalSize(): TermSize? {
    val model = getCurOutputModel()
    return model.editor.calculateTerminalSize()
  }

  override fun getTerminalSizeInitializedFuture(): CompletableFuture<*> {
    return TerminalUiUtils.getComponentSizeInitializedFuture(component)
  }

  override fun isFocused(): Boolean {
    return component.hasFocus()
  }

  private fun listenPanelSizeChanges() {
    component.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        val inputChannel = terminalSessionFuture.getNow(null)?.inputChannel ?: return
        val newSize = getTerminalSize() ?: return
        inputChannel.trySend(TerminalResizeEvent(newSize))
      }
    })
  }

  private fun listenAlternateBufferSwitch() {
    coroutineScope.launch(Dispatchers.EDT + CoroutineName("Alternate buffer switch listener")) {
      var isAlternateScreenBuffer = false
      sessionModel.terminalState.collect { state ->
        if (state.isAlternateScreenBuffer != isAlternateScreenBuffer) {
          isAlternateScreenBuffer = state.isAlternateScreenBuffer

          val model = if (state.isAlternateScreenBuffer) alternateBufferModel else outputModel
          terminalPanel.setTerminalContent(model.editor)
          IdeFocusManager.getInstance(project).requestFocus(terminalPanel.preferredFocusableComponent, true)
        }
      }
    }
  }

  private fun getCurOutputModel(): TerminalOutputModel {
    return if (sessionModel.terminalState.value.isAlternateScreenBuffer) alternateBufferModel else outputModel
  }

  private fun createOutputModel(
    editor: EditorEx,
    maxOutputLength: Int,
    settings: JBTerminalSystemSettingsProviderBase,
    sessionModel: TerminalSessionModel,
    encodingManager: TerminalKeyEncodingManager,
    terminalInput: TerminalInput,
    coroutineScope: CoroutineScope,
    withVerticalScroll: Boolean,
    withTopAndBottomInsets: Boolean,
  ): TerminalOutputModel {
    val model = TerminalOutputModelImpl(editor, maxOutputLength)

    TerminalCursorPainter.install(model, sessionModel, coroutineScope.childScope("TerminalCursorPainter"))

    val scrollingModel = if (withVerticalScroll) {
      TerminalOutputScrollingModelImpl(model, coroutineScope.childScope("TerminalOutputScrollingModel"))
    }
    else null

    if (withTopAndBottomInsets) {
      addTopAndBottomInsets(model.editor)
    }

    val eventsHandler = TerminalEventsHandlerImpl(sessionModel, model, encodingManager, terminalInput, settings, scrollingModel)
    val parentDisposable = coroutineScope.asDisposable()
    setupKeyEventDispatcher(model.editor, eventsHandler, parentDisposable)
    setupMouseListener(model.editor, sessionModel, settings, eventsHandler, parentDisposable)

    TerminalOutputEditorInputMethodSupport(
      model.editor,
      sendInputString = { text -> terminalInput.sendString(text) },
      getCaretPosition = {
        val offset = model.cursorOffsetState.value
        model.editor.offsetToLogicalPosition(offset)
      }
    ).install(parentDisposable)

    (model.editor.softWrapModel as? SoftWrapModelImpl)?.setSoftWrapPainter(EmptySoftWrapPainter)

    return model
  }

  private fun addTopAndBottomInsets(editor: Editor) {
    val inlayModel = editor.inlayModel

    val topRenderer = VerticalSpaceInlayRenderer(TerminalUi.blockTopInset)
    inlayModel.addBlockElement(0, false, true, TerminalUi.terminalTopInlayPriority, topRenderer)!!

    val bottomRenderer = VerticalSpaceInlayRenderer(TerminalUi.blockBottomInset)
    inlayModel.addBlockElement(editor.document.textLength, true, false, TerminalUi.terminalBottomInlayPriority, bottomRenderer)
  }

  private fun createOutputEditor(settings: JBTerminalSystemSettingsProviderBase, parentDisposable: Disposable): EditorEx {
    val document = createDocument()
    val editor = TerminalUiUtils.createOutputEditor(document, project, settings)
    editor.putUserData(TerminalDataContextUtils.IS_OUTPUT_MODEL_EDITOR_KEY, true)
    editor.settings.isUseSoftWraps = true
    editor.useTerminalDefaultBackground(parentDisposable = this)

    Disposer.register(parentDisposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    return editor
  }

  private fun createAlternateBufferEditor(settings: JBTerminalSystemSettingsProviderBase, parentDisposable: Disposable): EditorEx {
    val document = createDocument()
    val editor = TerminalUiUtils.createOutputEditor(document, project, settings)
    editor.putUserData(TerminalDataContextUtils.IS_ALTERNATE_BUFFER_MODEL_EDITOR_KEY, true)
    editor.useTerminalDefaultBackground(parentDisposable = this)
    editor.scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER

    Disposer.register(parentDisposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    return editor
  }

  private fun createDocument(): Document {
    val file = PsiFileFactory.getInstance(project).createFileFromText(
      "terminal_output",
      TerminalOutputFileType,
      "",
      LocalTimeCounter.currentTime(),
      true,
      true
    )
    return PsiDocumentManager.getInstance(project).getDocument(file)!!
  }

  override fun dispose() {}

  private inner class TerminalPanel(initialContent: Editor) : Wrapper(), UiDataProvider {
    private var curEditor: Editor = initialContent

    init {
      setTerminalContent(initialContent)
    }

    val preferredFocusableComponent: JComponent
      get() = curEditor.contentComponent

    fun setTerminalContent(editor: Editor) {
      curEditor = editor
      setContent(editor.component)
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[CommonDataKeys.EDITOR] = curEditor
      sink[TerminalInput.KEY] = terminalInput
    }
  }
}
