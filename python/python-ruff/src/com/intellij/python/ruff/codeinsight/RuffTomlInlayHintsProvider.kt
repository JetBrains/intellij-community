// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.python.ruff.RuffService
import com.intellij.python.ruff.codeinsight.RuffDocumentationUtil.isRuffCodeElement
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlLiteral

/**
 * Provides inlay hints for Ruff codes in pyproject.toml, ruff.toml, and .ruff.toml files.
 * Displays the rule name for each code in the ruff lint configuration sections.
 * For example: `"F108"` will show `"F108"unused-value`
 */
class RuffTomlInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    if (file !is TomlFile) return null
    if (!file.isRuffConfigFile) return null

    return Collector()
  }

  private class Collector() : SharedBypassCollector {
    val hintFormat = HintFormat.default

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      if (element is TomlLiteral && (element.isRuffCodeElement())) {
        processRuffCode(element, sink)
      }
    }

    private fun processRuffCode(element: TomlLiteral, sink: InlayTreeSink) {
      val code = element.text.trim('"', '\'')
      val ruffService = element.project.service<RuffService>()
      val ruleName = ruffService.ruleInformation[code]?.name
                     ?: ruffService.linterInformation[code.takeWhile { it.isLetter() }]
                     ?: return

      // Add inlay hint after the code
      // add a blank space to separate the code from the rule name
      sink.addPresentation(
        position = InlineInlayPosition(element.textRange.endOffset, false),
        hintFormat = hintFormat
      ) {
        text(ruleName)
      }
    }
  }
}
