// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.references.backtick

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.references.ReferenceUtil.hasMarkdownFiles

internal class BacktickReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
  override fun processQuery(
    queryParameters: ReferencesSearch.SearchParameters,
    consumer: Processor<in PsiReference>,
  ) {
    val target = queryParameters.elementToSearch
    if (target !is PsiNamedElement) return
    if (!hasMarkdownFiles(queryParameters.project)) return
    val text = target.name?.takeIf { it.isNotBlank() } ?: return

    queryParameters.optimizer.searchWord(
      text,
      PsiSearchScopeUtil.restrictScopeTo(queryParameters.effectiveSearchScope, MarkdownFileType.INSTANCE),
      UsageSearchContext.IN_PLAIN_TEXT,
      true,
      target
    )
  }
}
