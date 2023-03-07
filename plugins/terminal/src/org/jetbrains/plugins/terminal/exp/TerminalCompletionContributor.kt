// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.util.Alarm
import java.util.concurrent.CompletableFuture

class TerminalCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(TerminalPromptPanel.SESSION_KEY)
    if (session == null || parameters.completionType != CompletionType.BASIC) {
      return
    }

    val command = parameters.editor.document.getText(TextRange(0, parameters.offset))
    if (command.isBlank()) {
      return
    }

    val resultSet = result.caseInsensitive()
    val promptText = session.model.withLock { session.model.getAllText() }
    val baseTimeoutMillis = 1000
    var completionResult = invokeCompletion(session, command, promptText, baseTimeoutMillis)
    if (completionResult.isSingleItem) {
      val addedPart = completionResult.items.single()
      // Consider item, that ends with '/' as fully completed item
      if (!addedPart.endsWith('/')) {
        // Run completion again to check, that initially completed item was full
        // Consider the following example:
        // We get 'foo' as the only one completion item for command 'f'
        // But there can be more specific items: 'foobar' and 'fooboo'
        // And in this case we need to show these two items instead of incomplete 'foo'
        val secondResult = invokeCompletion(session, command + addedPart, promptText, baseTimeoutMillis / 2)
        if (secondResult.isSingleItem) {
          completionResult = CompletionResult(addedPart + secondResult.items.single())
        }
        else if (!secondResult.items.isEmpty()) {
          completionResult = secondResult
        }
      }
    }

    if (completionResult.isSingleItem) {
      val option = createOption(resultSet.prefixMatcher.prefix + completionResult.items.single())
      resultSet.addElement(option)
    }
    else {
      val options = completionResult.items.map { createOption(it) }
      resultSet.addAllElements(options)
    }

    resultSet.stopHere()
  }

  private fun invokeCompletion(session: TerminalSession,
                               command: String,
                               promptText: String,
                               timeoutMillis: Int): CompletionResult {
    val completionManager = session.completionManager
    val listenerDisposable = Disposer.newDisposable()
    val future = CompletableFuture<CompletionResult>()
    try {
      val model: TerminalModel = session.model
      val context = ParsingContext(command, promptText)
      model.addContentListener(object : TerminalModel.ContentListener {
        override fun onTextWritten(x: Int, y: Int, text: String) {
          val allText = model.getAllText(x + text.length, y)
          if (allText.isEmpty() || future.isDone) {
            return
          }
          context.addedText.append(text)
          val parsingResult = parseCompletionItems(allText, context)
          if (parsingResult.isSingleItem) {
            future.complete(CompletionResult(parsingResult.items.single()))
          }
          else if (parsingResult.items.isNotEmpty()) {
            future.complete(CompletionResult(parsingResult.items, parsingResult.newPromptShown))
          }
        }
      }, listenerDisposable)

      Alarm(Alarm.ThreadToUse.POOLED_THREAD, listenerDisposable).addRequest(Runnable {
        future.complete(CompletionResult(emptyList(), newPromptShown = false))
      }, timeoutMillis)

      completionManager.invokeCompletion(command)
      ApplicationUtil.runWithCheckCanceled(future, ProgressManager.getInstance().progressIndicator)
    }
    finally {
      Disposer.dispose(listenerDisposable)
      val newPromptShown = if (future.isDone) future.get().newPromptShown else false
      completionManager.resetPrompt(command.length, newPromptShown)
    }

    return if (future.isDone) {
      future.get()
    }
    else CompletionResult(emptyList(), newPromptShown = false)
  }

  private fun parseCompletionItems(text: String, context: ParsingContext): ParsingResult {
    val firstLine = text.substringBefore('\n')
    if (firstLine.startsWith(context.promptText)) {
      val completedCommand = firstLine.removePrefix(context.promptText).trim()
      if (completedCommand.startsWith(context.command)) {
        context.commandWritten = true
        val addedPart = completedCommand.removePrefix(context.command).trim()
        if (addedPart.isNotEmpty()) {
          // There is only one item that complete our command
          // So it is just added to the already typed text
          return ParsingResult(addedPart)
        }
      }
    }

    if (!context.commandWritten) {
      return ParsingResult()
    }

    val returnedToPrompt = context.addedText.endsWith(context.command)
    val newPromptShown = context.addedText.endsWith(context.promptText + context.command)

    return if ((returnedToPrompt || newPromptShown) && text.count { it == '\n' } > 0) {
      // Remove line with prompt and typed command
      var optionsText = text.substringAfter('\n').trim()
      if (newPromptShown) {
        // If the last line contain prompt, it means that all completion items do not fit inside terminal screen
        optionsText = optionsText.substringBeforeLast('\n').trim()
      }

      val items = optionsText.split(Regex("""(?<!\\)[ \n]+"""))
      if (items.firstOrNull() == "zsh:") {
        // TODO: it is a hack to not parse following zsh question:
        //  "zsh: do you wish to see all <N> possibilities (<M> lines)?"
        //  More general way of avoiding is required here
        ParsingResult()
      }
      else ParsingResult(items, newPromptShown)
    }
    else ParsingResult()
  }

  private fun createOption(option: String): LookupElement {
    return LookupElementBuilder.create(option)
  }

  private class ParsingContext(val command: String, val promptText: String) {
    var commandWritten: Boolean = false
    val addedText: StringBuilder = StringBuilder()
  }

  private open class ParsingResult private constructor(val items: List<String>, val isSingleItem: Boolean, val newPromptShown: Boolean) {
    constructor() : this(emptyList(), false, false)
    constructor(items: List<String>, newPromptShown: Boolean) : this(items, false, newPromptShown)
    constructor(item: String) : this(listOf(item), true, false)

    override fun toString(): String {
      return "isSingleItem: $isSingleItem, newPromptShown: $newPromptShown, items: $items"
    }
  }

  // Now it fully duplicates ParsingResult, but there can be additional fields in the future
  private class CompletionResult : ParsingResult {
    constructor(items: List<String>, newPromptShown: Boolean) : super(items, newPromptShown)
    constructor(item: String) : super(item)
  }
}