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
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.block.BlockTerminalScopeProvider
import org.jetbrains.plugins.terminal.block.TerminalFocusModel
import org.jetbrains.plugins.terminal.block.hyperlinks.TerminalHyperlinkHighlighter
import org.jetbrains.plugins.terminal.block.output.highlighting.CompositeTerminalTextHighlighter
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptRenderingInfo
import org.jetbrains.plugins.terminal.block.session.*
import org.jetbrains.plugins.terminal.block.ui.executeInBulk
import org.jetbrains.plugins.terminal.block.ui.getDisposed
import org.jetbrains.plugins.terminal.block.ui.invokeLater
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.IS_OUTPUT_EDITOR_KEY
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.*
import kotlin.math.max

/**
 * Designed as a part of MVC pattern.
 * @see TerminalOutputModel
 * @see TerminalOutputView
 * @see TerminalOutputController
 */
internal class TerminalOutputController(
  private val project: Project,
  private val editor: EditorEx,
  private val session: BlockTerminalSession,
  private val settings: JBTerminalSystemSettingsProviderBase,
  focusModel: TerminalFocusModel,
) : TerminalModel.TerminalListener {
  val outputModel: TerminalOutputModel = TerminalOutputModelImpl(editor)
  val selectionModel: TerminalSelectionModel = TerminalSelectionModel(outputModel)
  private val blocksDecorator: TerminalBlocksDecorator = TerminalBlocksDecorator(session.colorPalette, outputModel, focusModel, selectionModel, editor)
  private val textHighlighter: TerminalTextHighlighter = TerminalTextHighlighter(outputModel)

  private val blockCreationAlarm: Alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, session)

  /**
   * RequiresEdt: Should be accessed only from EDT
   */
  private var runningCommandContext: RunningCommandContext? = null

  @Volatile
  private var runningCommandInteractivity: RunningCommandInteractivity? = null

  private val hyperlinkHighlighter: TerminalHyperlinkHighlighter = TerminalHyperlinkHighlighter(project, outputModel, session)

  private val nextBlockCanBeStartedQueue: Queue<() -> Unit> = LinkedList()

  init {
    editor.putUserData(IS_OUTPUT_EDITOR_KEY, true)
    editor.highlighter = CompositeTerminalTextHighlighter(
      outputModel,
      textHighlighter,
      session
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
    scheduleLastOutputUpdate()

    invokeLater(editor.getDisposed(), ModalityState.any()) {
      val block = outputModel.getActiveBlock() ?: error("No active block")
      doWithScrollingAware {
        trimLastEmptyLine(block)
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

  private fun scheduleLastOutputUpdate() {
    val contentUpdatesScheduler = runningCommandInteractivity?.contentUpdatesScheduler
    val lastOutput: PartialCommandOutput? = if (contentUpdatesScheduler?.finished == false) {
      contentUpdatesScheduler.finishUpdating()
    }
    else {
      // There is no guarantee that command finish happen after contentUpdatesScheduler is installed on EDT.
      // So, it is a fallback for this case.
      // If the command finished so fast, then we consider all text buffer content as an output.
      val (output, terminalWidth) = session.model.withContentLock {
        ShellCommandOutputScraperImpl.scrapeOutput(session) to session.model.width
      }
      PartialCommandOutput(
        output.text,
        output.styleRanges,
        logicalLineIndex = 0,
        terminalWidth,
        isChangesDiscarded = false,
      )
    }

    if (lastOutput != null) {
      invokeLater(editor.getDisposed(), ModalityState.any()) {
        updateCommandOutput(lastOutput)
      }
    }
  }

  /**
   * Refines command output by dropping the trailing `\n` to avoid showing the last empty line in the command block.
   * Also, trims tailing whitespaces in case of Zsh: they are added to show '%' character at the end of the
   * last line without a newline.
   * Zsh adds the whitespaces after command finish and before calling `precmd` hook, so IDE cannot
   * identify correctly where command output ends exactly => trim tailing whitespaces as a workaround.

   * See `PROMPT_CR` and `PROMPT_SP` Zsh options, both are enabled by default:
   * https://zsh.sourceforge.io/Doc/Release/Options.html#Prompting
   *
   * Roughly, Zsh prints the following after each command and before prompt:
   * 1. `PROMPT_EOL_MARK` (by default, '%' for a normal user or a '#' for root)
   * 2. `$COLUMNS - 1` spaces
   * 3. \r
   * 4. A single space
   * 5. \r
   * https://github.com/zsh-users/zsh/blob/57248b88830ce56adc243a40c7773fb3825cab34/Src/utils.c#L1533-L1555
   *
   * Another workaround here is to add `unsetopt PROMPT_CR PROMPT_SP` to command-block-support.zsh,
   * but it will remove '%' mark on unterminated lines which can be unexpected for users.
   */
  private fun trimLastEmptyLine(block: CommandBlock) {
    // Return if there is no output or block is empty
    if (!block.withOutput) return

    // Count line break after the command as part of the output
    val outputStartOffset = block.outputStartOffset - if (block.withPrompt || block.withCommand) 1 else 0
    val outputText = editor.document.charsSequence.subSequence(outputStartOffset, block.endOffset)

    // Line break should always be present because we add it after the command text.
    val lastNewLineInd = outputText.lastIndexOf('\n')
    val lastLine = outputText.subSequence(lastNewLineInd + 1, outputText.length)
    val outputEndsWithNewline = lastLine.isEmpty()
    val outputEndsWithWhitespacesForZsh = session.shellIntegration.shellType == ShellType.ZSH && lastLine.isBlank()

    if (outputEndsWithNewline || outputEndsWithWhitespacesForZsh) {
      val trimStartOffset = outputStartOffset + max(0, lastNewLineInd)

      val highlightings = outputModel.getHighlightings(block)
        .filter { it.endOffset <= trimStartOffset }
      outputModel.putHighlightings(block, highlightings)

      editor.document.deleteString(trimStartOffset, block.endOffset)

      // We have to rerun the highlighters because deletion of the last line might cancel highlighting results applying.
      // TODO: can we rerun highlighting only on part of the block?
      hyperlinkHighlighter.highlightHyperlinks(block)
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

  @RequiresEdt(generateAssertion = false)
  private fun updateCommandOutput(output: PartialCommandOutput) {
    if (editor.isDisposed) {
      return
    }
    return doWithScrollingAware {
      doUpdateCommandOutput(output)
    }
  }

  @RequiresEdt(generateAssertion = false)
  private fun doUpdateCommandOutput(output: PartialCommandOutput) {
    val activeBlock = outputModel.getActiveBlock() ?: run {
      // If there is no active block, it means that it is the first content update. Create the new block here.
      blockCreationAlarm.cancelAllRequests()
      val context = runningCommandContext ?: run {
        thisLogger().error("No running command context")
        RunningCommandContext(null, null)
      }
      createNewBlock(context, output.terminalWidth)
    }
    updateBlock(activeBlock, output)
  }

  private fun createNewBlock(context: RunningCommandContext, terminalWidth: Int): CommandBlock {
    val block = outputModel.createBlock(context.command, context.prompt, terminalWidth)
    if (!block.textRange.isEmpty) {
      blocksDecorator.installDecoration(block)
    }
    return block
  }

  private fun updateBlock(block: CommandBlock, output: PartialCommandOutput) {
    // Execute update in the document bulk mode because it consists of several document changes.
    // Highlightings are requested on each change, and they might be not in sync with actual document content.
    // So better to use bulk mode to run highlighters after the actual change and avoid possible inconsistency.
    editor.document.executeInBulk {
      // add \n between command and output here (postponed from `TerminalOutputModel.createBlock`)
      val isPostponedNewLine = block.withPrompt || block.withCommand
      if (isPostponedNewLine && !block.withOutput) {
        editor.document.insertString(block.endOffset, "\n")
      }

      if (output.isChangesDiscarded) {
        // The output was so big, so the history buffer was overflown, and some changes were lost.
        // Consider all available lines in the block as trimmed
        block.trimmedLinesCount = output.logicalLineIndex
      }

      val outputStartLine = editor.document.getLineNumber(block.outputStartOffset)
      val replaceStartLine = outputStartLine + output.logicalLineIndex - block.trimmedLinesCount
      if (replaceStartLine >= editor.document.lineCount && editor.document.textLength > 0) {
        val newLines = "\n".repeat(replaceStartLine - editor.document.lineCount + 1)
        editor.document.insertString(editor.document.textLength, newLines)
      }

      val replaceStartOffset = editor.document.getLineStartOffset(replaceStartLine)
      editor.document.replaceString(replaceStartOffset, block.endOffset, output.text)

      updateHighlightings(block, replaceStartOffset, output.styles)
    }
    // Move trimming out of bulk update because it may delete some blocks and cause access to the editor UI caches.
    // Which is prohibited in the bulk mode.
    outputModel.trimOutput()

    // TODO: can we run highlighters only on the changed part of the block?
    hyperlinkHighlighter.highlightHyperlinks(block)

    // Install decorations lazily, only if there is some text.
    // ZSH prints '%' character on startup and then removing it immediately, so ignore this character to avoid blinking.
    // This hack can be solved by debouncing the update text requests.
    val outputText = if (block.withOutput) {
      editor.document.charsSequence.subSequence(block.outputStartOffset, block.endOffset)
    }
    else ""
    if (outputText.isNotBlank() && outputText.trim() != "%") {
      blocksDecorator.installDecoration(block)
    }

    // caret highlighter can be removed at this moment, because we replaced the text of the block
    // so, call repaint manually
    runningCommandInteractivity?.caretPainter?.repaint()
  }

  private fun updateHighlightings(block: CommandBlock, replaceOffset: Int, styles: List<StyleRange>) {
    val replaceHighlightings = styles.map {
      HighlightingInfo(replaceOffset + it.startOffset, replaceOffset + it.endOffset, it.style.toTextAttributesProvider())
    }
    val newHighlightings = outputModel.getHighlightings(block).asSequence()
      .filter { it.endOffset <= replaceOffset }
      .plus(replaceHighlightings)
      .toList()
    outputModel.putHighlightings(block, newHighlightings)
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

  private data class RunningCommandContext(val command: String?, val prompt: TerminalPromptRenderingInfo?)

  private inner class RunningCommandInteractivity(command: String?) {
    val disposable: Disposable = Disposer.newDisposable(session, "command $command")
    val caretModel = TerminalCaretModel(session, outputModel, editor, disposable)
    val caretPainter = TerminalCaretPainter(caretModel, outputModel, selectionModel, editor)
    val contentUpdatesScheduler: TerminalOutputContentUpdatesScheduler

    init {
      Disposer.register(session, disposable)
      Disposer.register(disposable, caretPainter)
      val eventsHandler = BlockTerminalEventsHandler(session, settings, this@TerminalOutputController)
      setupKeyEventDispatcher(editor, eventsHandler, disposable)
      setupMouseListener(editor, settings, session.model, eventsHandler, disposable)
      TerminalOutputEditorInputMethodSupport(editor, session, caretModel).install(disposable)
      contentUpdatesScheduler = setupContentUpdating()
    }

    private fun setupContentUpdating(): TerminalOutputContentUpdatesScheduler {
      val scope = BlockTerminalScopeProvider.getInstance(project).childScope("Command block content update")
      Disposer.register(disposable) {
        scope.cancel()
      }
      val collector = TerminalOutputContentUpdatesScheduler(session.model.textBuffer, session.shellIntegration, scope) { output ->
        updateCommandOutput(output)
      }
      collector.startUpdating()
      return collector
    }
  }

  companion object {
    val KEY: DataKey<TerminalOutputController> = DataKey.create("TerminalOutputController")
  }
}
