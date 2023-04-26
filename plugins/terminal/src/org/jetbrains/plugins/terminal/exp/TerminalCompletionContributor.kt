// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
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
    val prefix = calculatePrefix(command)
    val prefixMatcher = TerminalCompletionPrefixMatcher(prefix)
    val resultSet = result.caseInsensitive().withPrefixMatcher(prefixMatcher)

    val baseTimeoutMillis = 1000
    var completionResult = invokeCompletion(session, completionManager, command, baseTimeoutMillis)
    if (completionResult.isSingleItem) {
      val addedPart = completionResult.items.single().value
      // Consider item, that ends with '/' as fully completed item
      if (!addedPart.endsWith('/')) {
        // Run completion again to check, that initially completed item was full
        // Consider the following example:
        // We get 'foo' as the only one completion item for command 'f'
        // But there can be more specific items: 'foobar' and 'fooboo'
        // And in this case we need to show these two items instead of incomplete 'foo'
        val secondResult = invokeCompletion(session, completionManager, command + addedPart, baseTimeoutMillis / 2)
        if (secondResult.isSingleItem) {
          completionResult = CompletionResult(CompletionItem(addedPart + secondResult.items.single().value))
        }
        else if (!secondResult.items.isEmpty()) {
          completionResult = secondResult
        }
      }
    }

    val lookup = LookupManager.getActiveLookup(parameters.editor)
    if (completionResult.isSingleItem) {
      val option = createElement(CompletionItem(resultSet.prefixMatcher.prefix + completionResult.items.single().value), lookup)
      resultSet.addElement(option)
    }
    else {
      val options = completionResult.items.map { createElement(it, lookup) }
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
            future.complete(CompletionResult(CompletionItem(it)))
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

  private fun parseCompletionItems(text: String, context: ParsingContext): List<CompletionItem> {
    val optionsText = text.removePrefix(context.commandText).trim { it == ' ' || it == '\n' }
    val lines = optionsText.split("\n")
    val result = mutableListOf<CompletionItem>()
    for (line in lines) {
      val delimiter = " -- "
      val delimiterIndex = line.indexOf(delimiter)
      if (delimiterIndex != -1) {
        // item with description
        val values = line.substring(0, delimiterIndex).trim()
        val description = line.substring(delimiterIndex + delimiter.length).trim()
        values.split(SPLIT_REGEX).mapTo(result) { CompletionItem(it, description) }
      }
      else {
        val items = line.trim().split(SPLIT_REGEX)
        if (items.firstOrNull() == "zsh:") {
          // TODO: it is a hack to not parse following zsh question:
          //  "zsh: do you wish to see all <N> possibilities (<M> lines)?"
          //  More general way of avoiding is required here
          continue
        }
        items.mapTo(result) { CompletionItem(it) }
      }
    }
    return result
  }

  private fun createElement(item: CompletionItem, lookup: LookupEx?): LookupElement {
    return LookupElementBuilder.create(item.value)
      .withTypeText(item.description, true)
      .withInsertHandler { context, element ->
        val value = element.lookupString
        if (isSingleCharParameter(value) && lookup != null) {
          // Suppose we have the command 'ls -al' and want to add '-d' parameter
          // Because of the platform implementation it replaced the whole parameters string with '-d'.
          // At this moment we have 'ls -d', but we need 'ls -ald', so replace one parameter with their concatenation
          // TODO: using lookup here is a very dirty hack, because it is already disposed at this moment,
          //  but I failed to find the other way how to obtain prefix + user typings string
          val prefix = lookup.itemPattern(element)
          val toInsert = prefix + value.removePrefix("-")
          val editor = context.editor
          val startOffset = context.startOffset
          editor.document.replaceString(startOffset, startOffset + value.length, toInsert)
          editor.caretModel.moveToOffset(startOffset + toInsert.length)
        }
      }
  }

  /**
   * Find last command component that should be completed.
   * Command component delimiters are space (non escaped) and path separator.
   * We need to calculate the prefix independently, because prefix calculation in completion do count "-" as component body,
   * but in terminal it is a part of parameter component.
   * TODO: remove when PSI for terminal commands will be implemented
   *  (then it will be possible to properly calculate the prefix on the platform side).
   *  Or move this logic to inheritor of [com.intellij.codeInsight.lookup.LookupArranger].
   */
  private fun calculatePrefix(text: String): String {
    val delimiterIndex = text.withIndex().indexOfLast { (index, char) ->
      char == '/' || char == ' ' && (index == 0 || text[index - 1] != '\\')
    }
    return if (delimiterIndex != -1) text.substring(delimiterIndex + 1) else text
  }

  private class TerminalCompletionPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {
    override fun prefixMatches(name: String): Boolean {
      return if (isSingleCharParameter(name) && prefix.startsWith("-") && !prefix.startsWith("--")) {
        val parameter = name.removePrefix("-")
        !prefix.contains(parameter)
      }
      else StringUtil.startsWithIgnoreCase(name, prefix)
    }

    override fun cloneWithPrefix(prefix: String): PrefixMatcher {
      return TerminalCompletionPrefixMatcher(prefix)
    }
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

  private class CompletionResult private constructor(val items: List<CompletionItem>, val isSingleItem: Boolean) {
    constructor() : this(emptyList(), false)
    constructor(items: List<CompletionItem>) : this(items, false)
    constructor(item: CompletionItem) : this(listOf(item), true)

    override fun toString(): String {
      return "isSingleItem: $isSingleItem, items: $items"
    }
  }

  private data class CompletionItem(val value: String, val description: String? = null)

  companion object {
    private val SPLIT_REGEX: Regex = Regex("""(?<!\\) +""")

    private fun isSingleCharParameter(value: String): Boolean = value.length == 2 && value[0] == '-'
  }
}