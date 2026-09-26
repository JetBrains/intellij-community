// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.openapi.components.service
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiFile
import com.intellij.python.ruff.RuffService
import com.intellij.python.ruff.codeinsight.RuffDocumentationUtil.isRuffCodeElement
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlLiteral

/**
 * Provides documentation tooltips for Ruff error codes in ruff config files.
 * When hovering over a Ruff error code in a ruff config file, this provider
 * shows the rule documentation from RuffCodeService.
 */
class RuffErrorCodeDocumentationProvider : DocumentationTargetProvider {
  override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
    if (file !is TomlFile) {
      return emptyList()
    }

    val element = file.findElementAt(offset) ?: return emptyList()

    val literal = when {
      element is TomlLiteral -> element
      element.parent is TomlLiteral -> element.parent as TomlLiteral
      else -> return emptyList()
    }

    val text = literal.text.trim('"', '\'')

    if (!RuffDocumentationUtil.isRuffErrorCode(text)) {
      return emptyList()
    }

    if (!literal.isRuffCodeElement()) {
      return emptyList()
    }

    // Get the rule information from RuffCodeService
    val ruleInfo = literal.project.service<RuffService>().ruleInformation[text] ?: return emptyList()

    // Create and return a documentation target for this rule
    return listOf(RuffRuleDocumentationTarget(ruleInfo, literal.project))
  }

}
