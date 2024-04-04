// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.BlockTerminalColors
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.TextStyle
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.IS_OUTPUT_EDITOR_KEY
import org.jetbrains.plugins.terminal.exp.hyperlinks.TerminalHyperlinkHighlighter
import java.util.*

class TerminalOutputController(
  project: Project,
  private val editor: EditorEx,
  private val session: BlockTerminalSession,
  private val settings: JBTerminalSystemSettingsProviderBase,
  focusModel: TerminalFocusModel
) : TerminalModel.TerminalListener {
  val outputModel: TerminalOutputModel = TerminalOutputModel(editor)
  val selectionModel: TerminalSelectionModel = TerminalSelectionModel(outputModel)
  private val scraper: ShellCommandOutputScraper = ShellCommandOutputScraper(session)
  private val blocksDecorator: TerminalBlocksDecorator = TerminalBlocksDecorator(session.colorPalette, outputModel, focusModel, selectionModel, editor)
  private val textHighlighter: TerminalTextHighlighter = TerminalTextHighlighter(outputModel)

  private val blockCreationAlarm: Alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, session)

  private var runningCommandContext: RunningCommandContext? = null

  private var runningCommandInteractivity: RunningCommandInteractivity? = null

  private val hyperlinkHighlighter: TerminalHyperlinkHighlighter = TerminalHyperlinkHighlighter(project, outputModel, session)

  private val nextBlockCanBeStartedQueue: Queue<() -> Unit> = LinkedList()

  init {
    editor.putUserData(IS_OUTPUT_EDITOR_KEY, true)
    editor.highlighter = textHighlighter
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
  fun startCommandBlock(command: String?, prompt: PromptRenderingInfo?) {
    scrollToBottom()
    installRunningCommandInteractivity(command)
    runningCommandContext = RunningCommandContext(command, prompt)

    // Create a block forcefully in a timeout if there are no content updates. Command can output nothing for some time.
    val createBlockRequest: () -> Unit = {
      doWithScrollingAware {
        val context = runningCommandContext ?: error("No running command context")
        createNewBlock(context)
      }
    }
    blockCreationAlarm.addRequest(createBlockRequest, 200)
  }

  @RequiresEdt(generateAssertion = false)
  private fun installRunningCommandInteractivity(command: String?) {
    check(runningCommandInteractivity == null)
    runningCommandInteractivity = RunningCommandInteractivity(command)
  }

  @RequiresEdt(generateAssertion = false)
  private fun disposeRunningCommandInteractivity() {
    check(runningCommandInteractivity != null)
    Disposer.dispose(runningCommandInteractivity!!.disposable)
    runningCommandInteractivity = null
  }

  fun finishCommandBlock(exitCode: Int) {
    val output = scraper.scrapeOutput()
    invokeLater(editor.getDisposed(), ModalityState.any()) {
      val block = doWithScrollingAware {
        updateCommandOutput(output)
      }
      disposeRunningCommandInteractivity()
      val document = editor.document
      val lastLineInd = document.getLineNumber(block.endOffset)
      val lastLineStart = document.getLineStartOffset(lastLineInd)
      val lastLineText = document.getText(TextRange(lastLineStart, block.endOffset))
      // remove the line with empty prompt
      if (lastLineText.isBlank()) {
        // remove also the line break if it is not the first block
        val startRemoveOffset = lastLineStart - if (lastLineStart > 0) 1 else 0
        outputModel.deleteDocumentRange(block, TextRange(startRemoveOffset, block.endOffset))
      }
      if (document.getText(block.textRange).isBlank()) {
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
      runningCommandContext.takeIf { runningCommandInteractivity == null }?.let {
        installRunningCommandInteractivity(it.command)
      }
    }
  }

  private fun setupContentListener(disposable: Disposable) {
    scraper.addListener(object : ShellCommandOutputListener {
      override fun commandOutputChanged(output: StyledCommandOutput) {
        invokeLater(editor.getDisposed(), ModalityState.any()) {
          if (runningCommandContext != null) {
            doWithScrollingAware {
              updateCommandOutput(output)
            }
          }
        }
      }
    }, disposable, useExtendedDelayOnce = true)
  }

  @RequiresEdt(generateAssertion = false)
  private fun updateCommandOutput(output: StyledCommandOutput): CommandBlock {
    val activeBlock = outputModel.getActiveBlock() ?: run {
      // If there is no active block, it means that it is the first content update. Create the new block here.
      blockCreationAlarm.cancelAllRequests()
      val context = runningCommandContext ?: error("No running command context")
      createNewBlock(context)
    }
    updateBlock(activeBlock, toHighlightedCommandOutput(output, baseOffset = activeBlock.outputStartOffset))
    return activeBlock
  }

  private fun createNewBlock(context: RunningCommandContext): CommandBlock {
    val block = outputModel.createBlock(context.command, context.prompt)
    if (block.withPrompt) {
      val highlightings = adjustHighlightings(block.prompt!!.highlightings, block.startOffset)
      appendLineToBlock(block, block.prompt.text, highlightings, block.withCommand)
    }
    if (block.withCommand) {
      appendLineToBlock(block, context.command!!, listOf(createCommandHighlighting(block)), false)
    }
    if (block.withPrompt || block.withCommand) {
      blocksDecorator.installDecoration(block)
    }
    return block
  }

  private fun toHighlightedCommandOutput(output: StyledCommandOutput, baseOffset: Int): CommandOutput {
    return CommandOutput(output.text, output.styleRanges.map {
      HighlightingInfo(baseOffset + it.startOffset, baseOffset + it.endOffset, it.style.toTextAttributesProvider())
    })
  }

  private fun updateBlock(block: CommandBlock, output: CommandOutput) {
    // highlightings are collected only for output, so add prompt and command highlightings to the first place
    val highlightings = if (block.withPrompt || block.withCommand) {
      output.highlightings.toMutableList().also { highlightings ->
        if (block.withCommand) {
          highlightings.add(0, createCommandHighlighting(block))
        }
        if (block.withPrompt) {
          highlightings.addAll(0, adjustHighlightings(block.prompt!!.highlightings, block.startOffset))
        }
      }
    }
    else output.highlightings

    outputModel.putHighlightings(block, highlightings)
    // add leading \n here, because \n is not added after command in `startCommandBlock`
    val prefix = "\n".takeIf { block.withPrompt || block.withCommand }.orEmpty()
    editor.document.replaceString(block.outputStartOffset - prefix.length, block.endOffset, prefix + output.text)
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

  private fun appendLineToBlock(block: CommandBlock, text: String, highlightings: List<HighlightingInfo>, addTrailingNewLine: Boolean) {
    val existingHighlightings = outputModel.getHighlightings(block) ?: emptyList()
    outputModel.putHighlightings(block, existingHighlightings + highlightings)
    editor.document.insertString(block.endOffset, if (addTrailingNewLine) text + "\n" else text)
  }

  /** It is implied that [CommandBlock.command] is not null */
  private fun createCommandHighlighting(block: CommandBlock): HighlightingInfo {
    return HighlightingInfo(block.commandStartOffset, block.commandStartOffset + block.command!!.length,
                            TextAttributesKeyAdapter(editor, BlockTerminalColors.COMMAND))
  }

  private fun adjustHighlightings(highlightings: List<HighlightingInfo>, baseOffset: Int): List<HighlightingInfo> {
    return highlightings.map {
      HighlightingInfo(baseOffset + it.startOffset, baseOffset + it.endOffset, it.textAttributesProvider)
    }
  }

  fun addDocumentListener(listener: DocumentListener, disposable: Disposable? = null) {
    if (disposable != null) {
      editor.document.addDocumentListener(listener, disposable)
    }
    else editor.document.addDocumentListener(listener)
  }

  @RequiresEdt
  fun doWhenNextBlockCanBeStarted(callback: () -> Unit) {
    if (runningCommandContext == null && outputModel.getActiveBlock() == null) {
      callback()
    }
    else {
      nextBlockCanBeStartedQueue.offer(callback)
    }
  }

  private data class CommandOutput(val text: String, val highlightings: List<HighlightingInfo>)

  private data class RunningCommandContext(val command: String?, val prompt: PromptRenderingInfo?)

  private inner class RunningCommandInteractivity(command: String?) {
    val disposable: Disposable = Disposer.newDisposable(session, "command $command")
    val caretModel = TerminalCaretModel(session, outputModel, editor, disposable)
    val caretPainter = TerminalCaretPainter(caretModel, outputModel, selectionModel, editor)

    init {
      Disposer.register(disposable, caretPainter)
      val eventsHandler = BlockTerminalEventsHandler(session, settings, this@TerminalOutputController)
      setupKeyEventDispatcher(editor, settings, eventsHandler, outputModel, selectionModel, disposable)
      setupMouseListener(editor, settings, session.model, eventsHandler, disposable)
      setupContentListener(disposable)
    }
  }

  companion object {
    val KEY: DataKey<TerminalOutputController> = DataKey.create("TerminalOutputController")
  }
}