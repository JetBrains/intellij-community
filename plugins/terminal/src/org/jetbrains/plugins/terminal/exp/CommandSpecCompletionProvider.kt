// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sh.psi.ShSimpleCommand
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.TreeTraversal
import org.jetbrains.plugins.terminal.exp.completion.*

class CommandSpecCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(TerminalSession.KEY)
    if (session == null || parameters.completionType != CompletionType.BASIC) {
      return
    }

    val tokens = getCurCommandTokens(parameters)
    if (tokens.isEmpty()) {
      return
    }
    val commandName = tokens[0]
    val arguments = tokens.subList(1, tokens.size)
    if (arguments.isEmpty()) {
      return // command itself is incomplete
    }

    val elements = computeCompletionElements(commandName, arguments)
    if (elements == null) {
      return // failed to find completion spec for command
    }

    result.addAllElements(elements)
    result.stopHere()
  }

  private fun getCurCommandTokens(parameters: CompletionParameters): List<String> {
    val curElement = parameters.position
    val commandElement: ShSimpleCommand = PsiTreeUtil.getParentOfType(curElement, ShSimpleCommand::class.java)
                                          ?: return emptyList()
    val curElementEndOffset = curElement.textRange.endOffset
    return commandElement.children.filter { it.textRange.endOffset <= curElementEndOffset }
      .map { it.text.replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "") }
  }

  private fun computeCompletionElements(command: String, arguments: List<String>): List<LookupElement>? {
    val commandSpec: ShellSubcommand = CommandSpecManager.getInstance().getCommandSpec(command)
                                       ?: return null
    return computeCompletionElements(commandSpec, command, arguments)
  }

  private fun computeCompletionElements(spec: ShellSubcommand, command: String, arguments: List<String>): List<LookupElement> {
    val completeArguments = arguments.subList(0, arguments.size - 1)
    val suggestionsProvider = CommandTreeSuggestionsProvider()
    val rootNode: SubcommandNode = CommandTreeBuilder.build(suggestionsProvider, command, spec, completeArguments)
    val suggestions = computeSuggestions(suggestionsProvider, rootNode)
    return suggestions.flatMap { it.toLookupElements() }
  }

  private fun computeSuggestions(suggestionsProvider: CommandTreeSuggestionsProvider, root: SubcommandNode): List<BaseSuggestion> {
    val allChildren = TreeTraversal.PRE_ORDER_DFS.traversal(root as CommandPartNode<*>) { node -> node.children }
    val lastNode = allChildren.last() ?: root
    return suggestionsProvider.getSuggestionsOfNext(lastNode)
  }

  private fun BaseSuggestion.toLookupElements(): List<LookupElement> {
    return names.map { name ->
      LookupElementBuilder.create(insertValue ?: name)
        .withPresentableText(displayName ?: name)
        .withTypeText(description)
    }
  }
}