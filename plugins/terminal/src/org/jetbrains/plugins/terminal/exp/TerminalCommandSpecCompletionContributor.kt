// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.terminal.completion.CommandSpecCompletion
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.exp.completion.IJCommandSpecManager
import org.jetbrains.plugins.terminal.exp.completion.IJShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.terminal.completion.BaseSuggestion

class TerminalCommandSpecCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(TerminalSession.KEY)
    if (session == null || parameters.completionType != CompletionType.BASIC) {
      return
    }
    val shellType = session.shellIntegration?.shellType ?: return
    val shellSupport = TerminalShellSupport.findByShellType(shellType) ?: return

    val prefix = result.prefixMatcher.prefix.substringAfterLast('/') // take last part if it is a file path
    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix, true))

    val tokens = shellSupport.getCommandTokens(parameters.position) ?: return
    val suggestions = runBlockingCancellable {
      val runtimeDataProvider = IJShellRuntimeDataProvider(session)
      val completion = CommandSpecCompletion(IJCommandSpecManager.getInstance(), runtimeDataProvider)
      completion.computeCompletionItems(tokens) ?: emptyList()
    }

    val elements = suggestions.flatMap { it.toLookupElements() }
    resultSet.addAllElements(elements)
    resultSet.stopHere()
  }

  private fun BaseSuggestion.toLookupElements(): List<LookupElement> {
    return names.map { name ->
      val cursorOffset = insertValue?.indexOf("{cursor}")
      val realInsertValue = insertValue?.replace("{cursor}", "")
      // todo: command descriptions now exist only in english version
      //  need to find a way how to support translations
      @Suppress("HardCodedStringLiteral")
      val documentationTarget = description?.let { TerminalDocumentationTarget(name, it).createPointer() }
      val element = LookupElementBuilder.create(documentationTarget ?: this, realInsertValue ?: name)
        .withPresentableText(displayName ?: name)
        .withInsertHandler { context, _ ->
          if (cursorOffset != null && cursorOffset != -1) {
            context.editor.caretModel.moveToOffset(context.startOffset + cursorOffset)
          }
        }
      PrioritizedLookupElement.withPriority(element, priority / 100.0)
    }
  }

  private class TerminalDocumentationTarget(
    private val name: @NlsSafe String,
    private val description: @Nls String
  ) : Symbol, DocumentationTarget {
    override fun createPointer(): Pointer<out TerminalDocumentationTarget> {
      return Pointer.hardPointer(this)
    }

    override fun computePresentation(): TargetPresentation {
      return TargetPresentation.builder(name).presentation()
    }

    override fun computeDocumentation(): DocumentationResult {
      return DocumentationResult.documentation(description)
    }
  }
}