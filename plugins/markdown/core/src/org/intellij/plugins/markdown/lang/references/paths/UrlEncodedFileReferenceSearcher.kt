// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.references.paths

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.io.URLUtil
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.references.ReferenceUtil.hasMarkdownFiles

internal class UrlEncodedFileReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
  override fun processQuery(
    queryParameters: ReferencesSearch.SearchParameters,
    consumer: Processor<in PsiReference>,
  ) {
    if (!hasMarkdownFiles(queryParameters.project)) return

    val target = queryParameters.elementToSearch as? PsiFileSystemItem ?: return
    val virtualFile = target.virtualFile ?: return
    val fileName = virtualFile.nameWithoutExtension.ifEmpty { virtualFile.name }
    val encodedFileNames = setOf(URLUtil.encodePath(fileName), URLUtil.encodeURIComponent(fileName))
      .filter { it != fileName }
    if (encodedFileNames.isEmpty()) return
    val searchScope = PsiSearchScopeUtil.restrictScopeTo(queryParameters.effectiveSearchScope, MarkdownFileType.INSTANCE)

    for (encodedFileName in encodedFileNames) {
      queryParameters.optimizer.searchWord(
        encodedFileName,
        searchScope,
        UsageSearchContext.IN_PLAIN_TEXT,
        false,
        target,
      )
    }
  }
}
