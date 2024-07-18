// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.TextStyle
import org.jetbrains.plugins.terminal.block.TerminalFocusModel
import org.jetbrains.plugins.terminal.block.hyperlinks.TerminalHyperlinkHighlighter
import org.jetbrains.plugins.terminal.block.output.highlighting.CompositeTerminalTextHighlighter
import org.jetbrains.plugins.terminal.block.output.highlighting.TerminalCommandBlockHighlighter
import org.jetbrains.plugins.terminal.block.output.highlighting.TerminalCommandBlockHighlighterProvider.Companion.COMMAND_BLOCK_HIGHLIGHTER_PROVIDER_EP_NAME
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptRenderingInfo
import org.jetbrains.plugins.terminal.block.session.*
import org.jetbrains.plugins.terminal.block.ui.getDisposed
import org.jetbrains.plugins.terminal.block.ui.invokeLater
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.IS_OUTPUT_EDITOR_KEY
import java.util.*

/**
 * Designed as a part of MVC pattern.
 * @see TerminalOutputModel
 * @see TerminalOutputView
 * @see TerminalOutputController
 */
internal class TerminalOutputController(
  project: Project,
  private val editor: EditorEx,
  private val session: BlockTerminalSession,
  private val settings: JBTerminalSystemSettingsProviderBase,
  focusModel: TerminalFocusModel
) : TerminalModel.TerminalListener {
  val outputModel: TerminalOutputModel = TerminalOutputModelImpl(editor)
  val selectionModel: TerminalSelectionModel = TerminalSelectionModel(outputModel)
  private val scraper: IShellCommandOutputScraper = ShellCommandOutputScraperImpl(session)
  private val blocksDecorator: TerminalBlocksDecorator = TerminalBlocksDecorator(session.colorPalette, outputModel, focusModel, selectionModel, editor)
  private val textHighlighter: TerminalTextHighlighter = TerminalTextHighlighter(outputModel)

  private val blockCreationAlarm: Alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, session)

  /**
   * RequiresEdt: Should be accessed only from EDT
   */
  private var runningCommandContext: RunningCommandContext? = null

  private var runningCommandInteractivity: RunningCommandInteractivity? = null

  private val hyperlinkHighlighter: TerminalHyperlinkHighlighter = TerminalHyperlinkHighlighter(project, outputModel, session)

  private val nextBlockCanBeStartedQueue: Queue<() -> Unit> = LinkedList()

  private val commandBlockHighlighters: List<TerminalCommandBlockHighlighter> = COMMAND_BLOCK_HIGHLIGHTER_PROVIDER_EP_NAME
    .extensionList
    .map { it.getHighlighter(editor.colorsScheme) }

  init {
    editor.putUserData(IS_OUTPUT_EDITOR_KEY, true)
    editor.highlighter = CompositeTerminalTextHighlighter(
      outputModel,
      textHighlighter,
      commandBlockHighlighters.toMutableList()
    )
    session.model.addTerminalListener(this)

    session.addCommandListener(object : ShellCommandListener {
      override fun clearInvoked() {
        val disposable = Disposer.newDisposable(session)
        // clear all blocks when command is finished and then remove listener
        session.addCommandListener(object : ShellCommandListener {
          override fun commandFinished(event: CommandFinishedEvent) {
            invokeLater(editor.getDisposed(), ModalityState.any()) {
              outputModel.clearBlocks()
            }
            Disposer.dispose(disposable)
          }
        }, disposable)
      }
    })
  }

  @RequiresEdt
  fun startCommandBlock(
    command: String?,
    prompt: TerminalPromptRenderingInfo?,
  ) {
    scrollToBottom()
    installRunningCommandInteractivity(command)
    val newRunningCommandContext = RunningCommandContext(command, prompt)
    runningCommandContext = newRunningCommandContext

    // Create a block forcefully in a timeout if there are no content updates. Command can output nothing for some time.
    val createBlockRequest: () -> Unit = {
      doWithScrollingAware {
        val terminalWidth = session.model.withContentLock { session.model.width }
        createNewBlock(newRunningCommandContext, terminalWidth)
      }
    }
    blockCreationAlarm.addRequest(createBlockRequest, 200)
  }

  @RequiresEdt(generateAssertion = false)
  private fun installRunningCommandInteractivity(command: String?) {
    if (runningCommandInteractivity != null) {
      thisLogger().error("Running command interactivity is already installed")
      disposeRunningCommandInteractivity()
    }
    runningCommandInteractivity = RunningCommandInteractivity(command)
  }

  @RequiresEdt(generateAssertion = false)
  private fun disposeRunningCommandInteractivity() {
    runningCommandInteractivity ?: error("No running command interactivity")
    Disposer.dispose(runningCommandInteractivity!!.disposable)
    runningCommandInteractivity = null
  }

  fun finishCommandBlock(exitCode: Int) {
    val output = scraper.scrapeOutput()
    val terminalWidth = session.model.withContentLock { session.model.width }
    invokeLater(editor.getDisposed(), ModalityState.any()) {
      val block = doWithScrollingAware {
        updateCommandOutput(TerminalOutputSnapshot(terminalWidth, output), true)
      }
      disposeRunningCommandInteractivity()
      if (editor.document.getText(block.textRange).isBlank()) {
        outputModel.removeBlock(block)
      }
      else {
        outputModel.setBlockInfo(block, CommandBlockInfo(exitCode))
        outputModel.finalizeBlock(block)
      }
      runningCommandContext = null
      nextBlockCanBeStartedQueue.poll()?.invoke()
    }
  }

  @RequiresEdt
  fun insertEmptyLine() {
    editor.document.insertString(editor.document.textLength, "\n")
    scrollToBottom()
  }

  @RequiresEdt
  fun scrollToBottom() {
    val scrollingModel = editor.scrollingModel
    // disable animation to perform scrolling atomically
    scrollingModel.disableAnimation()
    try {
      val visibleArea = editor.scrollingModel.visibleArea
      scrollingModel.scrollVertically(editor.contentComponent.height - visibleArea.height)
    }
    finally {
      scrollingModel.enableAnimation()
    }
  }

  @RequiresEdt(generateAssertion = false)
  internal fun alternateBufferStateChanged(enabled: Boolean) {
    if (runningCommandContext == null) {
      thisLogger().warn("Alternate screen buffer changed ($enabled), but no running command")
    }
    if (enabled) {
      if (runningCommandInteractivity != null) {
        // stop updating the block content, because alternate buffer application will be shown in a separate component
        disposeRunningCommandInteractivity()
      }
    }
    else {
      val rcc = runningCommandContext
      if (rcc != null && runningCommandInteractivity == null) {
        installRunningCommandInteractivity(rcc.command)
      }
    }
  }

  private fun setupContentListener(disposable: Disposable) {
    scraper.addListener(object : ShellCommandOutputListener {
      override fun commandOutputChanged(output: StyledCommandOutput) {
        val terminalWidth = session.model.withContentLock { session.model.width }
        invokeLater(editor.getDisposed(), ModalityState.any()) {
          if (runningCommandContext != null) {
            doWithScrollingAware {
              updateCommandOutput(TerminalOutputSnapshot(terminalWidth, output), false)
            }
          }
        }
      }
    }, disposable, useExtendedDelayOnce = true)
  }

  @RequiresEdt(generateAssertion = false)
  private fun updateCommandOutput(snapshot: TerminalOutputSnapshot, finished: Boolean): CommandBlock {
    val activeBlock = outputModel.getActiveBlock() ?: run {
      // If there is no active block, it means that it is the first content update. Create the new block here.
      blockCreationAlarm.cancelAllRequests()
      val context = runningCommandContext ?: run {
        thisLogger().error("No running command context")
        RunningCommandContext(null, null)
      }
      createNewBlock(context, snapshot.width)
    }
    val output = if (finished) snapshot.output.dropLastBlankLine(session.shellIntegration.shellType) else snapshot.output
    updateBlock(activeBlock, toHighlightedCommandOutput(output, baseOffset = activeBlock.outputStartOffset), finished)
    return activeBlock
  }

  private fun createNewBlock(context: RunningCommandContext, terminalWidth: Int): CommandBlock {
    val block = outputModel.createBlock(context.command, context.prompt, terminalWidth)
    if (!block.textRange.isEmpty) {
      blocksDecorator.installDecoration(block)
    }
    return block
  }

  private fun toHighlightedCommandOutput(output: StyledCommandOutput, baseOffset: Int): TextWithHighlightings {
    return TextWithHighlightings(output.text, output.styleRanges.map {
      HighlightingInfo(baseOffset + it.startOffset, baseOffset + it.endOffset, it.style.toTextAttributesProvider())
    })
  }

  private fun updateBlock(block: CommandBlock, output: TextWithHighlightings, finished: Boolean) {
    // highlightings are collected only for output, so add prompt and command highlightings to the first place
    val highlightings = outputModel.getHighlightings(block).asSequence()
      .filter { it.endOffset <= block.outputStartOffset }
      .plus(output.highlightings)
      .toList()
    outputModel.putHighlightings(block, highlightings)

    // add \n between command and output here (postponed from `TerminalOutputModel.createBlock`)
    val isPostponedNewLine = block.withPrompt || block.withCommand
    val result = if (isPostponedNewLine && (!finished || output.text.isNotEmpty())) "\n" + output.text else output.text
    editor.document.replaceString(block.outputStartOffset - if (isPostponedNewLine) 1 else 0, block.endOffset, result)
    outputModel.trimOutput()
    hyperlinkHighlighter.highlightHyperlinks(block)

    // Install decorations lazily, only if there is some text.
    // ZSH prints '%' character on startup and then removing it immediately, so ignore this character to avoid blinking.
    // This hack can be solved by debouncing the update text requests.
    if (output.text.isNotBlank() && output.text.trim() != "%") {
      blocksDecorator.installDecoration(block)
    }

    // caret highlighter can be removed at this moment, because we replaced the text of the block
    // so, call repaint manually
    runningCommandInteractivity?.caretPainter?.repaint()
  }

  /**
   * Scroll to bottom if we were at the bottom before executing the [action]
   */
  @RequiresEdt(generateAssertion = false)
  private fun <T> doWithScrollingAware(action: () -> T): T {
    val wasAtBottom = editor.scrollingModel.visibleArea.let { it.y + it.height } == editor.contentComponent.height
    try {
      return action()
    }
    finally {
      if (wasAtBottom) {
        scrollToBottom()
      }
    }
  }

  private fun TextStyle.toTextAttributesProvider(): TextAttributesProvider = TextStyleAdapter(this, session.colorPalette)

  fun addDocumentListener(listener: DocumentListener, disposable: Disposable? = null) {
    if (disposable != null) {
      editor.document.addDocumentListener(listener, disposable)
    }
    else editor.document.addDocumentListener(listener)
  }

  /**
   * Wait for all blocks to finish and then invoke the [callback].
   */
  @RequiresEdt
  fun doWhenNextBlockCanBeStarted(callback: () -> Unit) {
    if (!isCommandRunning()) {
      callback()
    }
    else {
      nextBlockCanBeStartedQueue.offer(callback)
    }
  }

  @RequiresEdt(generateAssertion = true)
  fun isCommandRunning(): Boolean = runningCommandContext != null || outputModel.getActiveBlock() != null

  private data class TerminalOutputSnapshot(val width: Int, val output: StyledCommandOutput)

  private data class RunningCommandContext(val command: String?, val prompt: TerminalPromptRenderingInfo?)

  private inner class RunningCommandInteractivity(command: String?) {
    val disposable: Disposable = Disposer.newDisposable(session, "command $command")
    val caretModel = TerminalCaretModel(session, outputModel, editor, disposable)
    val caretPainter = TerminalCaretPainter(caretModel, outputModel, selectionModel, editor)

    init {
      Disposer.register(disposable, caretPainter)
      val eventsHandler = BlockTerminalEventsHandler(session, settings, this@TerminalOutputController)
      setupKeyEventDispatcher(editor, eventsHandler, disposable)
      setupMouseListener(editor, settings, session.model, eventsHandler, disposable)
      TerminalOutputEditorInputMethodSupport(editor, session, caretModel).install(disposable)
      setupContentListener(disposable)
    }
  }

  companion object {
    val KEY: DataKey<TerminalOutputController> = DataKey.create("TerminalOutputController")
  }
}
