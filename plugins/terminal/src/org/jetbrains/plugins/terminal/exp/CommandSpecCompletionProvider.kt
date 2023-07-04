// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sh.psi.ShSimpleCommand
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.TreeTraversal
import org.jetbrains.plugins.terminal.exp.completion.*
import org.jetbrains.terminal.completion.BaseSuggestion
import org.jetbrains.terminal.completion.ShellCommand

class CommandSpecCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(TerminalSession.KEY)
    if (session == null || parameters.completionType != CompletionType.BASIC) {
      return
    }

    val prefix = result.prefixMatcher.prefix.substringAfterLast('/') // take last part if it is a file path
    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix, true))

    val tokens = getCurCommandTokens(parameters)
    if (tokens.isEmpty()) {
      return
    }
    val commandName = tokens[0]
    val arguments = tokens.subList(1, tokens.size)
    if (arguments.isEmpty()) {
      return // command itself is incomplete
    }

    val elements = computeCompletionElements(session, commandName, arguments)
    if (elements == null) {
      return // failed to find completion spec for command
    }

    resultSet.addAllElements(elements)
    resultSet.stopHere()
  }

  private fun getCurCommandTokens(parameters: CompletionParameters): List<String> {
    val curElement = parameters.position
    val commandElement: ShSimpleCommand = PsiTreeUtil.getParentOfType(curElement, ShSimpleCommand::class.java)
                                          ?: return emptyList()
    val curElementEndOffset = curElement.textRange.endOffset
    return commandElement.children.filter { it.textRange.endOffset <= curElementEndOffset }
      .map { it.text.replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "") }
  }

  private fun computeCompletionElements(session: TerminalSession, command: String, arguments: List<String>): List<LookupElement>? {
    val commandSpec: ShellCommand = IJCommandSpecManager.getInstance().getCommandSpec(command)
                                    ?: return null
    return computeCompletionElements(session, commandSpec, command, arguments)
  }

  private fun computeCompletionElements(session: TerminalSession,
                                        spec: ShellCommand,
                                        command: String,
                                        arguments: List<String>): List<LookupElement> {
    val completeArguments = arguments.subList(0, arguments.size - 1)
    val lastArgument = arguments.last()
    val runtimeDataProvider = IJShellRuntimeDataProvider(session)
    val suggestionsProvider = CommandTreeSuggestionsProvider(runtimeDataProvider)
    val rootNode: SubcommandNode = CommandTreeBuilder.build(suggestionsProvider, IJCommandSpecManager.getInstance(),
                                                            command, spec, completeArguments)
    val suggestions = computeSuggestions(suggestionsProvider, rootNode, lastArgument)
    return suggestions.flatMap { it.toLookupElements() }
  }

  private fun computeSuggestions(suggestionsProvider: CommandTreeSuggestionsProvider,
                                 root: SubcommandNode,
                                 lastArgument: String): List<BaseSuggestion> {
    val allChildren = TreeTraversal.PRE_ORDER_DFS.traversal(root as CommandPartNode<*>) { node -> node.children }
    val lastNode = allChildren.last() ?: root
    return suggestionsProvider.getSuggestionsOfNext(lastNode, lastArgument).filter { s -> s.names.all { it.isNotEmpty() } }
  }

  private fun BaseSuggestion.toLookupElements(): List<LookupElement> {
    return names.map { name ->
      val cursorOffset = insertValue?.indexOf("{cursor}")
      val realInsertValue = insertValue?.replace("{cursor}", "")
      LookupElementBuilder.create(realInsertValue ?: name)
        .withPresentableText(displayName ?: name)
        .withTypeText(description)
        .withInsertHandler { context, item ->
          val editor = context.editor
          if (cursorOffset != null && cursorOffset != -1) {
            editor.caretModel.moveToOffset(context.startOffset + cursorOffset)
          }
          else {
            if (!item.lookupString.endsWith('/')) {
              // insert space only if not a filepath was completed
              editor.document.insertString(context.tailOffset, " ")
              editor.caretModel.moveToOffset(context.tailOffset + 1)
            }
            AutoPopupController.getInstance(context.project).scheduleAutoPopup(editor)
          }
        }
    }
  }
}