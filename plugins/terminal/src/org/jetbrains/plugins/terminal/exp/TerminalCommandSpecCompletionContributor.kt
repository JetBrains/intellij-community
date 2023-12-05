// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.terminal.completion.CommandSpecCompletion
import com.intellij.terminal.completion.CommandSpecCompletionUtil.isFilePath
import com.intellij.terminal.completion.CommandSpecCompletionUtil.isFolder
import com.intellij.terminal.completion.ShellArgumentSuggestion
import org.jetbrains.plugins.terminal.TerminalIcons
import org.jetbrains.plugins.terminal.exp.completion.IJCommandSpecManager
import org.jetbrains.plugins.terminal.exp.completion.IJShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.terminal.completion.BaseSuggestion
import org.jetbrains.terminal.completion.ShellArgument
import org.jetbrains.terminal.completion.ShellCommand
import org.jetbrains.terminal.completion.ShellOption
import javax.swing.Icon

class TerminalCommandSpecCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(TerminalSession.KEY)
    if (session == null || parameters.completionType != CompletionType.BASIC) {
      return
    }
    val shellSupport = TerminalShellSupport.findByShellType(session.shellIntegration.shellType) ?: return

    val prefix = result.prefixMatcher.prefix.substringAfterLast('/') // take last part if it is a file path
    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix, true))

    val tokens = shellSupport.getCommandTokens(parameters.position) ?: return
    val suggestions = runBlockingCancellable {
      val runtimeDataProvider = IJShellRuntimeDataProvider(session)
      val completion = CommandSpecCompletion(IJCommandSpecManager.getInstance(), runtimeDataProvider)
      val items = completion.computeCompletionItems(tokens)?.takeIf { it.isNotEmpty() }
      when {
        items != null -> items
        // suggest file names if there is nothing to suggest and completion is invoked manually
        !parameters.isAutoPopup -> completion.computeFileItems(tokens) ?: emptyList()
        else -> emptyList()
      }
    }

    val elements = suggestions.flatMap { it.toLookupElements() }
    resultSet.addAllElements(elements)
    resultSet.stopHere()
  }

  private fun BaseSuggestion.toLookupElements(): List<LookupElement> {
    val icon = findIcon()
    return names.map { name ->
      val cursorOffset = insertValue?.indexOf("{cursor}")
      val realInsertValue = insertValue?.replace("{cursor}", "")
      val nextSuggestions = getNextSuggestionsString(this).takeIf { it.isNotEmpty() }
      val escapedInsertValue = (realInsertValue ?: name).escapeSpaces()
      val element = LookupElementBuilder.create(this, escapedInsertValue)
        .withPresentableText(displayName ?: name)
        .withTailText(nextSuggestions, true)
        .withIcon(icon)
        .withInsertHandler { context, _ ->
          if (cursorOffset != null && cursorOffset != -1) {
            context.editor.caretModel.moveToOffset(context.startOffset + cursorOffset)
          }
        }
      PrioritizedLookupElement.withPriority(element, priority / 100.0)
    }
  }

  private fun String.escapeSpaces(): String = replace(" ", """\ """)

  private fun BaseSuggestion.findIcon(): Icon? {
    return when (this) {
      is ShellCommand -> TerminalIcons.Command
      is ShellOption -> TerminalIcons.Option
      is ShellArgumentSuggestion -> this.findIcon()
      else -> null
    }
  }

  private fun ShellArgumentSuggestion.findIcon(): Icon {
    return if (argument.isFilePath() || argument.isFolder()) {
      getFileIcon(names.first())
    }
    else TerminalIcons.Other
  }

  private fun getFileIcon(fileName: String): Icon {
    return if (fileName.endsWith("/") || fileName == "~" || fileName == "-") {
      AllIcons.Nodes.Folder
    }
    else {
      val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName)
      val fileIcon = fileType.icon ?: TerminalIcons.OtherFile
      if (fileType is UnknownFileType) TerminalIcons.OtherFile else fileIcon
    }
  }

  private fun getNextSuggestionsString(suggestion: BaseSuggestion): String {
    val result = when (suggestion) {
      is ShellCommand -> getNextOptionsAndArgumentsString(suggestion)
      is ShellOption -> getNextArgumentsString(suggestion.args)
      else -> ""
    }
    return if (result.isNotEmpty()) " $result" else ""
  }

  /** Returns required options and all arguments */
  private fun getNextOptionsAndArgumentsString(command: ShellCommand): String {
    val nextOptions = command.options.filter { it.isRequired }
    return buildString {
      for (option in nextOptions) {
        append(option.names.first())
        val arguments = getNextArgumentsString(option.args)
        if (arguments.isNotEmpty()) {
          append(' ')
          append(arguments)
        }
        append(' ')
      }
      append(getNextArgumentsString(command.args))
    }.trim()
  }

  private fun getNextArgumentsString(args: List<ShellArgument>): String {
    val argStrings = args.mapIndexed { index, arg -> arg.asSuggestionString(index) }
    return argStrings.joinToString(" ")
  }

  private fun ShellArgument.asSuggestionString(index: Int): String {
    val name = displayName ?: "arg${index + 1}"
    return if (isOptional) "[$name]" else "<$name>"
  }
}