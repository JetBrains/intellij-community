// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.find.SearchReplaceComponent
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
import com.intellij.openapi.editor.impl.EditorImpl
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
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.LocalTimeCounter
import com.intellij.util.asDisposable
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.TerminalContentView
import org.jetbrains.plugins.terminal.block.output.NEW_TERMINAL_OUTPUT_CAPACITY_KB
import org.jetbrains.plugins.terminal.block.output.TerminalOutputEditorInputMethodSupport
import org.jetbrains.plugins.terminal.block.output.TerminalTextHighlighter
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalOutputFileType
import org.jetbrains.plugins.terminal.block.reworked.session.*
import org.jetbrains.plugins.terminal.block.ui.*
import org.jetbrains.plugins.terminal.block.ui.TerminalUi.useTerminalDefaultBackground
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JScrollPane
import kotlin.math.min

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
  private val terminalSearchController: TerminalSearchController

  private val outputEditor: EditorEx
  private val alternateBufferEditor: EditorEx

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

    outputEditor = createOutputEditor(settings, parentDisposable = this)
    val outputModel = createOutputModel(
      editor = outputEditor,
      maxOutputLength = AdvancedSettings.getInt(NEW_TERMINAL_OUTPUT_CAPACITY_KB).coerceIn(1, 10 * 1024) * 1024,
      settings,
      sessionModel,
      encodingManager,
      terminalInput,
      coroutineScope.childScope("TerminalOutputModel"),
      withVerticalScroll = true,
      withTopAndBottomInsets = true
    )

    terminalSearchController = TerminalSearchController(project, outputEditor)

    alternateBufferEditor = createAlternateBufferEditor(settings, parentDisposable = this)
    val alternateBufferModel = createOutputModel(
      editor = alternateBufferEditor,
      maxOutputLength = 0,
      settings,
      sessionModel,
      encodingManager,
      terminalInput,
      coroutineScope.childScope("TerminalAlternateBufferModel"),
      withVerticalScroll = false,
      withTopAndBottomInsets = false
    )

    val blocksModel = TerminalBlocksModelImpl(outputEditor.document)
    TerminalBlocksDecorator(outputEditor, blocksModel, coroutineScope.childScope("TerminalBlocksDecorator"))

    controller = TerminalSessionController(
      sessionModel,
      outputModel,
      alternateBufferModel,
      blocksModel,
      settings,
      coroutineScope.childScope("TerminalSessionController")
    )

    terminalPanel = TerminalPanel(initialContent = outputEditor)

    listenSearchController()
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
    return getCurEditor().calculateTerminalSize()
  }

  override fun getTerminalSizeInitializedFuture(): CompletableFuture<*> {
    return TerminalUiUtils.getComponentSizeInitializedFuture(component)
  }

  override fun isFocused(): Boolean {
    return component.hasFocus()
  }

  private fun listenSearchController() {
    terminalSearchController.addListener(object : TerminalSearchControllerListener {
      override fun searchSessionStarted(session: TerminalSearchSession) {
        terminalPanel.installSearchComponent(session.component)
      }

      override fun searchSessionFinished(session: TerminalSearchSession) {
        terminalPanel.removeSearchComponent(session.component)
      }
    })
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

          val editor = if (state.isAlternateScreenBuffer) alternateBufferEditor else outputEditor
          terminalPanel.setTerminalContent(editor)
          IdeFocusManager.getInstance(project).requestFocus(terminalPanel.preferredFocusableComponent, true)
          if (state.isAlternateScreenBuffer) {
            terminalSearchController.finishSearchSession()
          }
        }
      }
    }
  }

  private fun getCurEditor(): EditorEx {
    return if (sessionModel.terminalState.value.isAlternateScreenBuffer) alternateBufferEditor else outputEditor
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
    val model = TerminalOutputModelImpl(editor.document, maxOutputLength)

    val parentDisposable = coroutineScope.asDisposable()

    // Document modifications can change the scroll position.
    // Mark them with the corresponding flag to indicate that this change is not caused by the explicit user action.
    model.addListener(parentDisposable, object : TerminalOutputModelListener {
      override fun beforeContentChanged() {
        editor.isTerminalOutputScrollChangingActionInProgress = true
      }

      override fun afterContentChanged(startOffset: Int) {
        editor.isTerminalOutputScrollChangingActionInProgress = false

        // Also repaint the changed part of the document to ensure that highlightings are properly painted.
        editor.repaint(startOffset, editor.document.textLength)
      }
    })

    editor.highlighter = TerminalTextHighlighter { model.getHighlightings() }

    TerminalCursorPainter.install(editor, model, sessionModel, coroutineScope.childScope("TerminalCursorPainter"))

    val scrollingModel = if (withVerticalScroll) {
      TerminalOutputScrollingModelImpl(editor, model, coroutineScope.childScope("TerminalOutputScrollingModel"))
    }
    else null

    if (withTopAndBottomInsets) {
      addTopAndBottomInsets(editor)
    }

    val eventsHandler = TerminalEventsHandlerImpl(sessionModel, editor, encodingManager, terminalInput, settings, scrollingModel)
    setupKeyEventDispatcher(editor, eventsHandler, parentDisposable)
    setupMouseListener(editor, sessionModel, settings, eventsHandler, parentDisposable)

    TerminalOutputEditorInputMethodSupport(
      editor,
      sendInputString = { text -> terminalInput.sendString(text) },
      getCaretPosition = {
        val offset = model.cursorOffsetState.value
        editor.offsetToLogicalPosition(offset)
      }
    ).install(parentDisposable)

    (editor.softWrapModel as? SoftWrapModelImpl)?.setSoftWrapPainter(EmptySoftWrapPainter)

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
    val editor = createEditor(document, settings)
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
    val editor = createEditor(document, settings)
    editor.putUserData(TerminalDataContextUtils.IS_ALTERNATE_BUFFER_MODEL_EDITOR_KEY, true)
    editor.useTerminalDefaultBackground(parentDisposable = this)
    editor.scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER

    Disposer.register(parentDisposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    return editor
  }

  private fun createEditor(
    document: Document,
    settings: JBTerminalSystemSettingsProviderBase,
  ): EditorImpl {
    val result = TerminalUiUtils.createOutputEditor(document, project, settings, installContextMenu = false)
    result.contextMenuGroupId = "Terminal.ReworkedTerminalContextMenu"
    result.softWrapModel.applianceManager.setSoftWrapsUnderScrollBar(true)
    return result
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

  private inner class TerminalPanel(initialContent: Editor) : JBLayeredPane(), UiDataProvider {
    private var curEditor: Editor = initialContent

    init {
      setTerminalContent(initialContent)
    }

    val preferredFocusableComponent: JComponent
      get() = curEditor.contentComponent

    fun setTerminalContent(editor: Editor) {
      val prevEditor = curEditor
      @Suppress("SENSELESS_COMPARISON") // called from init when curEditor == null
      if (prevEditor != null) {
        remove(curEditor.component)
      }
      curEditor = editor
      addToLayer(editor.component, DEFAULT_LAYER)
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[CommonDataKeys.EDITOR] = curEditor
      sink[TerminalInput.KEY] = terminalInput
      sink[TerminalSearchController.KEY] = terminalSearchController
    }

    fun installSearchComponent(component: SearchReplaceComponent) {
      addToLayer(component, POPUP_LAYER)
    }

    fun removeSearchComponent(component: SearchReplaceComponent) {
      remove(component)
    }

    override fun getPreferredSize(): Dimension {
      return if (curEditor.document.textLength == 0) Dimension() else (curEditor as EditorImpl).preferredSize
    }

    override fun doLayout() {
      for (component in components) {
        when (component) {
          curEditor.component -> layoutEditor(component)
          is SearchReplaceComponent -> layoutSearchComponent(component)
        }
      }
    }

    private fun layoutEditor(component: Component) {
      component.setBounds(0, 0, width, height)
    }

    private fun layoutSearchComponent(component: Component) {
      val prefSize = component.preferredSize
      val maxSize = component.maximumSize
      val compWidth = minOf(width, prefSize.width, maxSize.width)
      val compHeight = min(prefSize.height, maxSize.height)
      component.setBounds(width - compWidth, 0, compWidth, compHeight)
    }
  }
}
