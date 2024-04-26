package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.find.usages.api.PsiUsage
import com.intellij.refactoring.rename.api.PsiModifiableRenameUsage
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.refactoring.rename.api.RenameUsageSearchParameters
import com.intellij.refactoring.rename.api.RenameUsageSearcher
import com.intellij.util.Query
import org.intellij.plugins.markdown.model.psi.MarkdownPsiUsage
import org.intellij.plugins.markdown.model.psi.headers.MarkdownDirectUsageQuery

internal class HtmlAnchorRenameUsageSearcher: RenameUsageSearcher {
  override fun collectSearchRequests(parameters: RenameUsageSearchParameters): Collection<Query<out RenameUsage>> {
    val target = parameters.target
    if (target !is HtmlAnchorSymbolRenameTarget) {
      return emptyList()
    }
    val symbol = target.symbol
    val project = parameters.project
    val searchScope = parameters.searchScope
    val references = MarkdownHtmlAnchorPsiReferenceSearcher.buildSearchRequest(project, symbol, searchScope)
    val usages = references.mapping { MarkdownPsiUsage.create(it) }
    val selfUsage = MarkdownDirectUsageQuery(MarkdownPsiUsage.create(symbol.file, symbol.range, declaration = true))
    return listOf(
      usages.toModifiableRenameUsageQuery(),
      selfUsage.toModifiableRenameUsageQuery()
    )
  }

  private fun Query<out PsiUsage>.toModifiableRenameUsageQuery(): Query<PsiModifiableRenameUsage> {
    return mapping { PsiModifiableRenameUsage.defaultPsiModifiableRenameUsage(it) }
  }
}
