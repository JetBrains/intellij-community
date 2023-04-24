// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.util.Alarm
import java.util.concurrent.CompletableFuture

class TerminalCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(TerminalSession.KEY)
    val completionManager = parameters.editor.getUserData(TerminalCompletionManager.KEY)
    if (session == null || completionManager == null || parameters.completionType != CompletionType.BASIC) {
      return
    }

    val rawCommand = parameters.editor.document.getText(TextRange(0, parameters.offset))
    if (rawCommand.isBlank()) {
      return
    }
    // remove explicit terminal line breaks
    val command = rawCommand.replace("\\\n", "")

    val resultSet = result.caseInsensitive()
    val baseTimeoutMillis = 1000
    var completionResult = invokeCompletion(session, completionManager, command, baseTimeoutMillis)
    if (completionResult.isSingleItem) {
      val addedPart = completionResult.items.single()
      // Consider item, that ends with '/' as fully completed item
      if (!addedPart.endsWith('/')) {
        // Run completion again to check, that initially completed item was full
        // Consider the following example:
        // We get 'foo' as the only one completion item for command 'f'
        // But there can be more specific items: 'foobar' and 'fooboo'
        // And in this case we need to show these two items instead of incomplete 'foo'
        val secondResult = invokeCompletion(session, completionManager, command + addedPart, baseTimeoutMillis / 2)
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
                               completionManager: TerminalCompletionManager,
                               command: String,
                               timeoutMillis: Int): CompletionResult {
    completionManager.waitForTerminalLock()

    val model: TerminalModel = session.model
    val listenerDisposable = Disposer.newDisposable()
    val future = CompletableFuture<CompletionResult>()
    val context = ParsingContext(command, model.width)
    try {
      model.addContentListener(object : TerminalModel.ContentListener {
        override fun onTextWritten(x: Int, y: Int, text: String) {
          val allText = model.getAllText(x + text.length, y)
          if (allText.isEmpty() || future.isDone) {
            return
          }
          parseSingleItem(allText, context)?.let {
            future.complete(CompletionResult(it))
          }
        }
      }, listenerDisposable)

      session.addCommandListener(object : ShellCommandListener {
        override fun promptShown() {
          val allText = model.withContentLock { model.getAllText() }
          if (allText.isEmpty() || future.isDone) {
            return
          }
          val items = parseCompletionItems(allText, context)
          future.complete(CompletionResult(items))
        }
      }, listenerDisposable)

      Alarm(Alarm.ThreadToUse.POOLED_THREAD, listenerDisposable).addRequest(Runnable {
        future.complete(CompletionResult())
      }, timeoutMillis)

      completionManager.invokeCompletion(command)
      ApplicationUtil.runWithCheckCanceled(future, ProgressManager.getInstance().progressIndicator)
    }
    finally {
      Disposer.dispose(listenerDisposable)
      if (!future.isDone) {
        future.complete(CompletionResult())
      }
      ApplicationManager.getApplication().executeOnPooledThread {
        completionManager.resetPrompt()
      }
    }

    return future.getNow(CompletionResult())
  }

  private fun parseSingleItem(text: String, context: ParsingContext): String? {
    if (text.startsWith(context.commandText)) {
      val addedPart = text.removePrefix(context.commandText).substringBefore('\n')
      if (addedPart.isNotBlank()) {
        // There is only one item that complete our command
        // So it is just added to the already typed text
        return addedPart
      }
    }
    return null
  }

  private fun parseCompletionItems(text: String, context: ParsingContext): List<String> {
    val optionsText = text.removePrefix(context.commandText).trim { it == ' ' || it == '\n' }
    val items = optionsText.split(Regex("""(?<!\\)[ \n]+"""))
    return if (items.firstOrNull() == "zsh:") {
      // TODO: it is a hack to not parse following zsh question:
      //  "zsh: do you wish to see all <N> possibilities (<M> lines)?"
      //  More general way of avoiding is required here
      emptyList()
    }
    else items
  }

  private fun createOption(option: String): LookupElement {
    return LookupElementBuilder.create(option)
  }

  private class ParsingContext(command: String, terminalWidth: Int) {
    val commandText: String = createCommandString(command, terminalWidth)

    private fun createCommandString(command: String, width: Int): String {
      val builder = StringBuilder(command)
      var pos = width
      while (pos < builder.length) {
        builder.insert(pos, '\n')
        pos += width + 1
      }
      return builder.toString()
    }
  }

  private class CompletionResult private constructor(val items: List<String>, val isSingleItem: Boolean) {
    constructor() : this(emptyList(), false)
    constructor(items: List<String>) : this(items, false)
    constructor(item: String) : this(listOf(item), true)

    override fun toString(): String {
      return "isSingleItem: $isSingleItem, items: $items"
    }
  }
}