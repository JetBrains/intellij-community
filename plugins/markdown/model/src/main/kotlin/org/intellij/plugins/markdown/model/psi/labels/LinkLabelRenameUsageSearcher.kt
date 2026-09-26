package org.intellij.plugins.markdown.model.psi.labels

import com.intellij.refactoring.rename.api.PsiModifiableRenameUsage
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.refactoring.rename.api.RenameUsageSearchParameters
import com.intellij.refactoring.rename.api.RenameUsageSearcher
import com.intellij.util.Query
import org.intellij.plugins.markdown.model.psi.MarkdownPsiUsage
import org.intellij.plugins.markdown.model.psi.MarkdownSymbolUsageSearcher
import org.intellij.plugins.markdown.model.psi.headers.MarkdownDirectUsageQuery

internal class LinkLabelRenameUsageSearcher: RenameUsageSearcher {
  override fun collectSearchRequests(parameters: RenameUsageSearchParameters): Collection<Query<out RenameUsage>> {
    val target = parameters.target
    if (target !is LinkLabelSymbol) {
      return emptyList()
    }
    val searchText = target.searchText.takeIf { it.isNotEmpty() } ?: return emptyList()
    val usages = MarkdownSymbolUsageSearcher.buildSearchRequest(parameters.project, target, searchText, parameters.searchScope)
    val selfUsage = MarkdownDirectUsageQuery(MarkdownPsiUsage.create(target.file, target.range, declaration = true))
    val modifiedUsages = usages.mapping(PsiModifiableRenameUsage::defaultPsiModifiableRenameUsage)
    val modifiedSelfUsage = selfUsage.mapping(PsiModifiableRenameUsage::defaultPsiModifiableRenameUsage)
    return listOf(modifiedUsages, modifiedSelfUsage)
  }
}
