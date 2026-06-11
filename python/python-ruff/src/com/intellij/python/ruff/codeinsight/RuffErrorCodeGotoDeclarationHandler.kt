// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.openapi.components.service
import com.intellij.openapi.paths.WebReference
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.elementType
import com.intellij.python.ruff.RuffService
import com.intellij.python.ruff.RuffUtil.RUFF_WEBSITE
import com.intellij.python.ruff.codeinsight.RuffDocumentationUtil.isRuffCodeElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyFile
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlLiteral


/**
 * Provides web references functionality for Ruff error codes in pyproject.toml files
 * and Python file `noqa` comments.
 *
 * When clicking on a Ruff error code, this handler navigates to the
 * corresponding documentation page on the Ruff website.
 *
 * For Python files, it supports error codes in comments like:
 * - `# noqa: F401`
 * - `# noqa: E501, F401`
 * - `# ruff: noqa: F401`
 *
 * For TOML files, it supports error codes in Ruff configuration sections.
 *
 * we aren't using a `GotoDeclarationHandler` because they appear withing comments
 */
internal object RuffErrorCodeReferenceProvider : PsiReferenceProvider() {
  override fun acceptsTarget(target: PsiElement): Boolean = false

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<out PsiReference> {
    return when (element.containingFile) {
      is PyFile -> handlePythonFile(element)
      is TomlFile -> handleTomlFile(element)
      else -> null
    } ?: PsiReference.EMPTY_ARRAY
  }

  /**
   * Handles navigation for Ruff error codes in TOML files.
   */
  private fun handleTomlFile(element: PsiElement): Array<PsiReference>? {
    val literal = when {
      element is TomlLiteral -> element
      element.parent is TomlLiteral -> element.parent as TomlLiteral
      else -> return null
    }


    val text = literal.text.trim('"', '\'')

    if (!RuffDocumentationUtil.CODE_PATTERN.matches(text) && !RuffDocumentationUtil.RUFF_LINTER_PATTERN.matches(text)) {
      return null
    }

    if (!literal.isRuffCodeElement()) {
      return null
    }

    return arrayOf(createWebReference(literal, text))
  }

  /**
   * Handles navigation for Ruff error codes in Python file `noqa` comments.
   */
  private fun handlePythonFile(sourceElement: PsiElement): Array<PsiReference>? {
    val comment = when {
      sourceElement is PsiComment -> sourceElement
      sourceElement.parent is PsiComment -> sourceElement.parent as PsiComment
      else -> return null
    }

    if (comment.elementType != PyTokenTypes.END_OF_LINE_COMMENT) {
      return null
    }

    val match = RuffDocumentationUtil.SUPPRESSION_PATTERN.find(comment.text) ?: return null

    val allCodesGroup = match.groups[1] ?: return null

    return RuffDocumentationUtil.CODE_PATTERN.findAll(comment.text, startIndex = allCodesGroup.range.first).map { code ->
      createWebReference(comment, code.value, TextRange(code.range.first, code.range.last + 1))
    }.toList().toTypedArray()
  }

  private fun createWebReference(element: PsiElement, errorCode: String, textRange: TextRange? = null): PsiReference {
    val ruffService = element.project.service<RuffService>()

    val url = ruffService.ruleInformation[errorCode]?.let {
      "$RUFF_WEBSITE/rules/${it.name}/"
    } ?: run {
      val linterCode = errorCode.takeWhile { it.isLetter() }
      ruffService.linterInformation[linterCode]?.let { linterName ->
        "$RUFF_WEBSITE/rules/#${linterName.lowercase().replace(" ", "-")}-${linterCode.lowercase()}"
      }
    } ?: "$RUFF_WEBSITE/rules/"

    return WebReference(element, textRange, url).apply {
      // while these do link to web, they aren't "urls" and shouldn't be highlighted
      highlight = false
    }
  }
}

private class RuffErrorCodeReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(), RuffErrorCodeReferenceProvider)
  }
}
