// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.components.service
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LookupElementDocumentationTargetProvider
import com.intellij.psi.PsiFile
import com.intellij.python.ruff.RuffService

/**
 * Provides documentation for Ruff error code completion items.
 */
class RuffErrorCodeCompletionDocumentationProvider : LookupElementDocumentationTargetProvider {
  override fun documentationTarget(psiFile: PsiFile, element: LookupElement, offset: Int): DocumentationTarget? {
    val code = element.lookupString

    if (!RuffDocumentationUtil.isRuffErrorCode(code)) {
      return null
    }

    val ruleInfo = psiFile.project.service<RuffService>().ruleInformation[code] ?: return null

    return RuffRuleDocumentationTarget(ruleInfo, psiFile.project)
  }
}
