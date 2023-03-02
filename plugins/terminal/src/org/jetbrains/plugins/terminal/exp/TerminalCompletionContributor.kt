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
import java.util.*
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
    val baseTimeoutMillis = 1000
    val completionResult = invokeCompletion(session, command, resultSet, baseTimeoutMillis)
    if (completionResult.isSingleItem) {
      val addedPart = completionResult.items.single()
      val completedItem = resultSet.prefixMatcher.prefix + addedPart
      if (!addedPart.endsWith('/')) {
        // Run completion again to check, that initially completed item was full
        // Consider the following example:
        // We get 'foo' as the only one completion item for command 'f'
        // But there can be more specific items: 'foobar' and 'fooboo'
        // And in this case we need to show these two items instead of incomplete 'foo'
        val finalResult = invokeCompletion(session, command + addedPart, resultSet, baseTimeoutMillis / 2)
        // Add initial completion item, if there are no more specific items
        // If there are such items, they are already added inside 'invokeCompletion'
        if (finalResult.items.isEmpty()) {
          resultSet.addElement(createOption(completedItem))
        }
      }
      else {
        // Consider item, that ends with '/' as fully completed item
        resultSet.addElement(createOption(completedItem))
      }
    }

    resultSet.stopHere()
  }

  private fun invokeCompletion(session: TerminalSession,
                               command: String,
                               resultSet: CompletionResultSet,
                               timeoutMillis: Int): CompletionResult {
    val completionManager = session.completionManager
    val listenerDisposable = Disposer.newDisposable()
    val addedItems: MutableSet<String> = Collections.synchronizedSet(HashSet())
    val future = CompletableFuture<CompletionResult>()
    try {
      val model: TerminalModel = session.model
      val promptText = model.withLock { model.getAllText().trim() }
      model.addContentListener(object : TerminalModel.ContentListener {
        override fun onContentChanged() {
          val text = model.getAllText().trim()
          if (text.isEmpty() || future.isDone) {
            return
          }
          val parsingResult = parseCompletionItems(text, command, promptText)
          if (parsingResult.isSingleItem) {
            future.complete(CompletionResult(parsingResult.items.single()))
          }
          else {
            val newItems = parsingResult.items.filter { item ->
              !addedItems.contains(item) && (!item.endsWith('/') || !addedItems.contains(item.removeSuffix("/")))
            }
            addedItems.addAll(newItems)
            val options = newItems.map { createOption(it) }
            resultSet.addAllElements(options)
            if (parsingResult.newPromptShown) {
              future.complete(CompletionResult(addedItems.toList(), newPromptShown = true))
            }
          }
        }
      }, listenerDisposable)

      Alarm(Alarm.ThreadToUse.POOLED_THREAD, listenerDisposable).addRequest(Runnable {
        future.complete(CompletionResult(addedItems.toList(), newPromptShown = false))
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
    else CompletionResult(addedItems.toList(), newPromptShown = false)
  }

  private fun parseCompletionItems(text: String, command: String, promptText: String): ParsingResult {
    val firstLine = text.substringBefore('\n')
    if (firstLine.startsWith(promptText)) {
      val completedCommand = firstLine.removePrefix(promptText).trim()
      if (completedCommand.startsWith(command)) {
        val addedPart = completedCommand.removePrefix(command).trim()
        if (addedPart.isNotEmpty()) {
          // There is only one item that complete our command
          // So it is just added to the already typed text
          return ParsingResult(addedPart)
        }
      }
    }

    val linesCount = text.count { it == '\n' }
    return if (linesCount > 0) {
      var optionsText = text
      if (text.startsWith(promptText)) {
        // Remove line with prompt and typed command
        optionsText = optionsText.substringAfter('\n')
      }
      val newPromptShown = text.substringAfterLast('\n').startsWith(promptText)
      if (newPromptShown) {
        // If the last line contain prompt, it means that all completion items do not fit inside terminal screen
        // Also it means that completion is finished
        optionsText = optionsText.substringBeforeLast('\n')
      }
      optionsText = optionsText.trim { it == ' ' || it == '\n' }

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