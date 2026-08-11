// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.patterns.PlatformPatterns.psiComment
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.python.ruff.RuffService
import com.intellij.python.ruff.codeinsight.RuffDocumentationUtil.isRuffCodeElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyFile
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral

/**
 * Provides code completion for Ruff error codes in Python comments and TOML files.
 * In the completion item, both the error code and its name are shown.
 */
class RuffErrorCodeCompletionContributor : CompletionContributor() {

  init {
    val provider = RuffErrorCodeCompletionProvider()
    // Python `ruff: noqa:` completion
    extend(
      CompletionType.BASIC,
      psiComment().withParent<PyFile>().withText(StandardPatterns.string().startsWith("# ruff: noqa:")),
      provider
    )
    // Python `noqa: ` completion
    extend(
      CompletionType.BASIC,
      psiComment().withText(StandardPatterns.string().startsWith("# noqa:")),
      provider
    )

    // TOML file completion
    extend(
      CompletionType.BASIC,
      tomlRuffLintPattern(),
      provider
    )
  }

  /**
   * Pattern to match TOML elements where Ruff error codes can be completed.
   */
  private fun tomlRuffLintPattern(): PsiElementPattern.Capture<PsiElement> {
    return psiElement()
      .withLanguage(TomlLanguage)
      .withParent(
        psiElement<TomlLiteral>()
          .with("ruffLintKey") { literal, _ ->
            literal.isRuffCodeElement()
          }
          .withParent(
            psiElement<TomlArray>()
              .withParent<TomlKeyValue>()
          )
      )
  }

  /**
   * Completion provider for Ruff error codes.
   * Provides completion items with both the error code and its name.
   */
  private class RuffErrorCodeCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      // prevent the default stuff from appearing
      result.stopHere()
      val project = parameters.position.project
      val ruffService = project.service<RuffService>()

      for ((code, ruleInfo) in ruffService.ruleInformation) {
        result.addElement(
          LookupElementBuilder.create(code)
            .withPresentableText(code)
            .withTypeText(ruleInfo.name, true)
            .withTailText(" (${ruleInfo.linter})", true)
            .withLookupString(ruleInfo.name)
            .withCaseSensitivity(true),
        )
      }

      // Add TOML config exclusive options
      if (parameters.position.containingFile.language == TomlLanguage) {
        // Add special "ALL" code for selecting all rules
        result.addElement(
          PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create("ALL")
              .withPresentableText("ALL")
              .withTypeText("All Ruff rules", true)
              .withLookupString("everything")
              .withLookupString("all rules")
              .withLookupString("all linters")
              .withTailText(" (all rules)", true)
              .withCaseSensitivity(true),
            1.0, // want it to appear at the top of the results
          )
        )

        // Add linter prefixes
        for ((prefix, linterName) in ruffService.linterInformation) {
          result.addElement(
            LookupElementBuilder.create(prefix)
              .withPresentableText(prefix)
              .withTypeText("All $linterName rules", true)
              .withTailText(" ($linterName)", true)
              .withLookupString(linterName)
              .withCaseSensitivity(true)
          )
        }
      }
    }
  }
}