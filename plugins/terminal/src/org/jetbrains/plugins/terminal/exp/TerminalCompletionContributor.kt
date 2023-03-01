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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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

    val completionResult = result.caseInsensitive()
    val completionManager = session.completionManager
    val listenerDisposable = Disposer.newDisposable()
    val future = CompletableFuture<Boolean>()
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
            val item = completionResult.prefixMatcher.prefix + parsingResult.items.single()
            completionResult.addElement(createOption(item))
            future.complete(false)
          }
          else {
            val options = parsingResult.items.map { createOption(it) }
            completionResult.addAllElements(options)
            if (parsingResult.newPromptShown) {
              future.complete(true)
            }
          }
        }
      }, listenerDisposable)

      future.completeOnTimeout(false, 1000L, TimeUnit.MILLISECONDS)
      completionManager.invokeCompletion(command)
      ApplicationUtil.runWithCheckCanceled(future, ProgressManager.getInstance().progressIndicator)
    }
    finally {
      Disposer.dispose(listenerDisposable)
      completionManager.resetPrompt(command.length, newPromptShown = future.getNow(false))
    }

    completionResult.stopHere()
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

  private class ParsingResult private constructor(val items: List<String>, val isSingleItem: Boolean, val newPromptShown: Boolean) {
    constructor() : this(emptyList(), false, false)
    constructor(items: List<String>, newPromptShown: Boolean) : this(items, false, newPromptShown)
    constructor(item: String) : this(listOf(item), true, false)

    override fun toString(): String {
      return "isSingleItem: $isSingleItem, newPromptShown: $newPromptShown, items: $items"
    }
  }
}