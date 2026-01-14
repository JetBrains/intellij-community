package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupArranger.DefaultArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.toRelative
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration

/**
 * Manages the currently running terminal command completion process in the project.
 * Use [invokeCompletion] to schedule a new completion request.
 * Use [activeProcess] to get the currently running completion session.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class TerminalCommandCompletionService(
  private val project: Project,
  coroutineScope: CoroutineScope,
) {
  @get:RequiresEdt
  internal var activeProcess: TerminalCommandCompletionProcess? = null
    private set

  private val requestsCount = MutableStateFlow(0)
  private val requestsChannel = Channel<TerminalCommandCompletionContext>(
    capacity = Channel.CONFLATED,
    onUndeliveredElement = { requestsCount.update { it - 1 } }
  )

  init {
    coroutineScope.launch(Dispatchers.UiWithModelAccess + CoroutineName("Completion requests processing")) {
      requestsChannel.consumeAsFlow().collectLatest { request ->
        try {
          processCompletionRequest(request)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          LOG.error("Exception during completion requests processing", e)
        }
        finally {
          requestsCount.update { it - 1 }
        }
      }
    }
  }

  @RequiresEdt
  fun invokeCompletion(
    terminalView: TerminalView,
    editor: Editor,
    outputModel: TerminalOutputModel,
    shellIntegration: TerminalShellIntegration,
    isAutoPopup: Boolean,
  ) {
    if (shellIntegration.outputStatus.value != TerminalOutputStatus.TypingCommand) {
      return
    }

    // Ensure that the editor caret is synced with the terminal one
    val cursorOffset = outputModel.cursorOffset
    editor.caretModel.primaryCaret.moveToOffset(cursorOffset.toRelative(outputModel))

    val activeBlock = shellIntegration.blocksModel.activeBlock as TerminalCommandBlock
    val commandText = getRawCommandText(activeBlock, outputModel) ?: return

    val context = TerminalCommandCompletionContext(
      project = project,
      terminalView = terminalView,
      editor = editor,
      outputModel = outputModel,
      shellIntegration = shellIntegration,
      commandStartOffset = activeBlock.commandStartOffset!!,
      initialCursorOffset = cursorOffset,
      commandText = commandText,
      isAutoPopup = isAutoPopup,
    )

    requestsCount.update { it + 1 }
    val result = requestsChannel.trySend(context)
    if (!result.isSuccess) {
      requestsCount.update { it - 1 }
    }
  }

  private suspend fun processCompletionRequest(context: TerminalCommandCompletionContext) = coroutineScope {
    // Pass the current scope to the completion process to link the lifecycles of the process and the current completion request.
    val process = createCompletionProcess(context, coroutineScope = this)
    activeProcess = process
    try {
      val shouldShow = withContext(Dispatchers.Default) {
        val result = getCompletionSuggestions(context)
        if (result != null && shouldShowCompletion(context, result.suggestions)) {
          submitSuggestions(process, result)
          true
        }
        else false
      }
      if (!shouldShow) {
        return@coroutineScope
      }

      if (checkContextValid(context)) {
        // Show the lookup only if context is still valid
        if (process.tryInsertOrShowPopup()) {
          // If a lookup was shown, leave the process alive until the shown lookup is closed
          awaitCancellation()
        }
      }
      else if (process.lookup.isAvailableToUser && !process.isPopupMeaningless()) {
        // Restart the process again in case the lookup is already showing and valid,
        // but the current context became outdated.
        process.scheduleRestart()
      }
    }
    finally {
      // The coroutine can be canceled at this moment, and the logic of closing the Lookup might not expect it.
      // So, let's close the Lookup in the non-cancellable context to avoid problems.
      withContext(NonCancellable) {
        process.cancel()
        activeProcess = null
      }
    }
  }

  private suspend fun getCompletionSuggestions(context: TerminalCommandCompletionContext): TerminalCommandCompletionResult? {
    val commandSpecResult = TerminalCommandSpecCompletionContributor().getCompletionSuggestions(context)
    val powershellResult = if (!context.isAutoPopup && ShellName.isPowerShell(context.shellName)) {
      PowerShellCompletionContributor().getCompletionSuggestions(context)
    }
    else null

    return if (commandSpecResult != null && commandSpecResult.suggestions.isNotEmpty()
               && powershellResult != null && powershellResult.suggestions.isNotEmpty()) {
      if (commandSpecResult.prefix == powershellResult.prefix) {
        val suggestions = (powershellResult.suggestions + commandSpecResult.suggestions).distinctBy { it.name }
        TerminalCommandCompletionResult(suggestions, commandSpecResult.prefix)
      }
      else if (powershellResult.prefix.length > commandSpecResult.prefix.length) {
        powershellResult
      }
      else commandSpecResult
    }
    else if (powershellResult != null && powershellResult.suggestions.isNotEmpty()) {
      powershellResult
    }
    else commandSpecResult
  }

  private val TerminalCommandCompletionContext.shellName: ShellName
    // Startup options should be initialized at this point
    get() = terminalView.startupOptionsDeferred.getNow()?.guessShellName() ?: ShellName.of("unknown")

  private fun submitSuggestions(
    process: TerminalCommandCompletionProcess,
    result: TerminalCommandCompletionResult,
  ) {
    val prefixReplacementIndex = result.suggestions.firstOrNull()?.prefixReplacementIndex ?: 0
    val prefix = result.prefix.substring(prefixReplacementIndex)
    val prefixMatcher = PlainPrefixMatcher(prefix, true)
    val sorter = CompletionService.getCompletionService().defaultSorter(process.parameters, prefixMatcher)

    val items = result.suggestions.mapNotNull {
      val element = it.toLookupElement()
      CompletionResult.wrap(element, prefixMatcher, sorter)
    }
    process.addItems(items)
  }

  /**
   * Returns true if the cursor is at the same position and there is the same command text that was
   * at the initialization of the completion context.
   */
  @RequiresEdt
  private fun checkContextValid(context: TerminalCommandCompletionContext): Boolean {
    val outputModel = context.outputModel
    val activeBlock = context.shellIntegration.blocksModel.activeBlock as TerminalCommandBlock
    val curCommandText = getRawCommandText(activeBlock, outputModel)?.let {
      val cursorOffset = outputModel.cursorOffset - activeBlock.commandStartOffset!!
      it.substring(0, cursorOffset.toInt())
    }
    val contextCommandText = context.commandText.let {
      val cursorOffset = context.initialCursorOffset - context.commandStartOffset
      it.substring(0, cursorOffset.toInt())
    }
    return outputModel.cursorOffset == context.initialCursorOffset
           && curCommandText == contextCommandText
  }

  @RequiresEdt
  private fun createCompletionProcess(
    context: TerminalCommandCompletionContext,
    coroutineScope: CoroutineScope,
  ): TerminalCommandCompletionProcess {
    val lookup = obtainLookup(context.editor, project, context.isAutoPopup)
    val process = TerminalCommandCompletionProcess(context, lookup, coroutineScope)
    val arranger = TerminalCompletionLookupArranger(process)
    process.setLookupArranger(arranger)
    return process
  }

  private fun shouldShowCompletion(context: TerminalCommandCompletionContext, suggestions: List<ShellCompletionSuggestion>): Boolean {
    return !context.isAutoPopup
           || TerminalOptionsProvider.instance.commandCompletionShowingMode == TerminalCommandCompletionShowingMode.ALWAYS
           || isSuggestingOnlyParameters(suggestions)
  }

  private fun isSuggestingOnlyParameters(suggestions: List<ShellCompletionSuggestion>): Boolean {
    // Show the popup only if there are no suggestions for subcommands (only options and arguments).
    return suggestions.none { it.type == ShellSuggestionType.COMMAND }
  }

  /**
   * Returns command text with possible trailing new lines and spaces.
   * Returns null if the cursor is in the incorrect place or the block is invalid.
   */
  private fun getRawCommandText(block: TerminalCommandBlock, model: TerminalOutputModel): String? {
    val start = block.commandStartOffset ?: return null
    val end = block.endOffset
    if (start < model.startOffset || start > model.endOffset
        || end < model.startOffset || end > model.endOffset
        || start > end) {
      return null
    }
    return model.getText(start, end).toString()
  }

  @RequiresEdt
  private fun obtainLookup(editor: Editor, project: Project, isAutoPopup: Boolean): LookupImpl {
    val existing = LookupManager.getActiveLookup(editor) as? LookupImpl
    if (existing != null && existing.isCompletion) {
      existing.markReused()
      existing.putUserData(TerminalCommandCompletion.LAST_SELECTED_ITEM_KEY, null)
      if (!isAutoPopup) {
        existing.setLookupFocusDegree(LookupFocusDegree.FOCUSED)
      }
      return existing
    }

    val arranger = object : DefaultArranger() {
      override fun isCompletion(): Boolean {
        return true
      }
    }
    val lookup = LookupManager.getInstance(project).createLookup(editor, LookupElement.EMPTY_ARRAY, "", arranger) as LookupImpl
    lookup.setLookupFocusDegree(if (isAutoPopup) LookupFocusDegree.SEMI_FOCUSED else LookupFocusDegree.FOCUSED)
    return lookup
  }

  private fun ShellCompletionSuggestion.toLookupElement(): LookupElement {
    val actualIcon = icon ?: TerminalCompletionUtil.findIconForSuggestion(name, type)
    val nextSuggestions = TerminalCompletionUtil.getNextSuggestionsString(this).takeIf { it.isNotEmpty() }

    val element = LookupElementBuilder.create(this, name)
      .withPresentableText(displayName ?: name)
      .withTailText(nextSuggestions, true)
      .withIcon(TerminalStatefulDelegatingIcon(actualIcon))
    // Actual insertion logic is performed in TerminalLookupListener
    element.putUserData(CodeCompletionHandlerBase.DIRECT_INSERTION, true)

    val adjustedPriority = priority.coerceIn(0, 100)
    return PrioritizedLookupElement.withPriority(element, adjustedPriority / 100.0)
  }

  @TestOnly
  suspend fun awaitPendingRequestsProcessed() {
    requestsCount.first { it == 0 }
  }

  companion object {
    fun getInstance(project: Project): TerminalCommandCompletionService = project.service()

    private val LOG = logger<TerminalCommandCompletionService>()
  }
}