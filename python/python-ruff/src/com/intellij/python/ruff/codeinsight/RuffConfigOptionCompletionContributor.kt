// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.codeInsight.completion.BaseCompletionService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProcessEx
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.ruff.RuffService
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader
import kotlin.math.max

/**
 * Provides code completion for Ruff configuration options in TOML files.
 */
class RuffConfigOptionCompletionContributor : CompletionContributor() {

  init {
    extend(
      CompletionType.BASIC,
      psiElement().inFile(
        psiElement<TomlFile>().withName(".ruff.toml", "ruff.toml", PY_PROJECT_TOML)
      ),
      RuffConfigOptionCompletionProvider()
    )
  }
}

/**
 * Completion provider for Ruff configuration options.
 * Provides completion items with both the option name and its documentation.
 */
private class RuffConfigOptionCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val project = parameters.position.project
    val ruffService = project.service<RuffService>()

    val configOptions = ruffService.configOptions
    val configGroups = ruffService.configOptionGroups

    val position = parameters.position

    if (configGroups.isEmpty()) {
      return
    }

    /**
     *  Case 1: Completing inside a table header, e.g., [tool.ruff.lint.ru<caret>]
     */
    fun completeInsideTableHeader(): Boolean {
      var e: PsiElement? = position
      while (e != null && e !is TomlTableHeader) e = e.parent
      val header = e ?: return false
      val key = header.key ?: return true
      val full = key.text
      if (header.containingFile.name == PY_PROJECT_TOML && !full.startsWith("tool.ruff")) return true
      val ruffPathRaw = full.removePrefix("tool.ruff").trimStart('.')
      val parts = if (ruffPathRaw.isEmpty()) emptyList() else ruffPathRaw.split('.')
      val prefixParts = if (parts.isEmpty()) emptyList() else parts.dropLast(1)

      addGroupSegments(prefixParts, parameters, result, configGroups)
      return true
    }

    /**
     *  Case 2: Completing in the body of a \[tool.ruff...] table (new key at blank line, etc.)
     */
    fun completeInTableBody(): Boolean {
      var e: PsiElement? = position
      if (position.parent !is TomlKeySegment) return false
      while (e != null && e !is TomlTable) e = e.parent
      val full = (position.parent as TomlKeySegment).fullName ?: return false
      if (position.containingFile.name == PY_PROJECT_TOML && !full.startsWith("tool.ruff")) return false

      val ruffPathRaw = full.removePrefix("tool.ruff").trimStart('.')
      val prefixParts = if (ruffPathRaw.isEmpty()) emptyList() else ruffPathRaw.split('.').dropLast(1)

      // First, groups (nested tables)
      addGroupSegments(prefixParts, parameters, result, configGroups)

      // then keys (leaf options)
      for ((path, configInfo) in configOptions) {
        val parts = path.split('.')
        if (parts.dropLast(1) == prefixParts) {
          val segment = parts.last()
          result.addElement(
            LookupElementBuilder.create(segment)
              .withCaseSensitivity(false)
              .withTypeText(configInfo.valueType, true)
              .withTailText(" default: ${configInfo.default}", true)
          )
        }
      }
      return true
    }


    if (completeInsideTableHeader()) return
    if (completeInTableBody()) return
  }

  /**
   * Helper to add next-level group segments under a given prefix
   */
  private fun addGroupSegments(prefixParts: List<String>, parameters: CompletionParameters, result: CompletionResultSet, configGroups: Set<String> = emptySet()) {
    // remove junk words
    (parameters.process as CompletionProcessEx).putUserData(BaseCompletionService.FORBID_WORD_COMPLETION, true)
    // prevent the default stuff from appearing
    result.stopHere()

    for (option in configGroups) {
      val optionParts = option.split('.')
      if (optionParts.size == prefixParts.size + 1 && optionParts.take(max(0, prefixParts.size - 1)) == prefixParts.dropLast(1)) {
        val segment = optionParts.last()
        result.addElement(
          LookupElementBuilder.create(segment)
            .withCaseSensitivity(false)
        )
      }
    }
  }
}
