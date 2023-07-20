package org.intellij.plugins.markdown.model.psi.labels

import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.*
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
    val modifiedUsages = usages.mapping { LinkLabelModifiableRenameUsage(it.file, it.range, declaration = false) }
    val modifiedSelfUsage = selfUsage.mapping { LinkLabelModifiableRenameUsage(it.file, it.range, declaration = true) }
    return listOf(modifiedUsages, modifiedSelfUsage)
  }

  private class LinkLabelModifiableRenameUsage(
    override val file: PsiFile,
    override val range: TextRange,
    override val declaration: Boolean
  ): PsiModifiableRenameUsage {
    override fun createPointer(): Pointer<out LinkLabelModifiableRenameUsage> {
      return createPointer(file, range, declaration)
    }

    // Inplace rename for such case is not supported yet
    override val fileUpdater: ModifiableRenameUsage.FileUpdater
      get() = fileRangeUpdater(this::buildNewName)

    private fun buildNewName(name: String): String {
      return buildString {
        if (!name.startsWith("[")) {
          append("[")
        }
        append(name)
        if (!name.startsWith("]")) {
          append("]")
        }
      }
    }

    companion object {
      private fun createPointer(file: PsiFile, range: TextRange, declaration: Boolean): Pointer<LinkLabelModifiableRenameUsage> {
        return Pointer.fileRangePointer(file, range) { restoredFile, restoredRange ->
          LinkLabelModifiableRenameUsage(restoredFile, restoredRange, declaration)
        }
      }
    }
  }
}
