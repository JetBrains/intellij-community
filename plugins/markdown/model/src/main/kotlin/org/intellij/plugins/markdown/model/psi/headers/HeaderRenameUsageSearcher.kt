package org.intellij.plugins.markdown.model.psi.headers

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.*
import com.intellij.util.Query
import com.intellij.util.text.StringOperation
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.model.psi.MarkdownPsiUsage
import org.intellij.plugins.markdown.model.psi.MarkdownSymbolUsageSearcher

internal class HeaderRenameUsageSearcher: RenameUsageSearcher {
  override fun collectSearchRequests(parameters: RenameUsageSearchParameters): Collection<Query<out RenameUsage>> {
    val target = parameters.target
    if (target !is HeaderSymbol) {
      return emptyList()
    }
    val searchText = target.searchText.takeIf { it.isNotEmpty() } ?: return emptyList()
    val usages = MarkdownSymbolUsageSearcher.buildSearchRequest(parameters.project, target, searchText, parameters.searchScope)
    val selfUsage = MarkdownDirectUsageQuery(MarkdownPsiUsage.create(target.file, target.range, declaration = true))
    val modifiedUsages = usages.mapping { HeaderAnchorModifiableRenameUsage(it.file, it.range) }
    val modifiedSelfUsage = selfUsage.mapping { ModifiableRenameUsageWrapper(it) }
    return listOf(modifiedUsages, modifiedSelfUsage)
  }

  private class ModifiableRenameUsageWrapper(
    usage: PsiUsage
  ): PsiModifiableRenameUsage by PsiModifiableRenameUsage.defaultPsiModifiableRenameUsage(usage) {
    override val declaration = true

    override val fileUpdater = updater

    private class Updater: ModifiableRenameUsage.FileUpdater by idFileRangeUpdater()

    companion object {
      private val updater = Updater()
    }
  }

  private class HeaderAnchorModifiableRenameUsage(
    override val file: PsiFile,
    override val range: TextRange
  ): PsiModifiableRenameUsage {
    override fun createPointer(): Pointer<out PsiModifiableRenameUsage> {
      return Pointer.fileRangePointer(file, range, HeaderRenameUsageSearcher::HeaderAnchorModifiableRenameUsage)
    }

    override val declaration: Boolean = false

    override val fileUpdater: ModifiableRenameUsage.FileUpdater
      get() = HeaderAnchorFileUpdater(file, range)

    private class HeaderAnchorFileUpdater(
      private val file: PsiFile,
      private val range: TextRange
    ): ModifiableRenameUsage.FileUpdater {
      override fun prepareFileUpdateBatch(usages: Collection<ModifiableRenameUsage>, newName: String): Collection<FileOperation> {
        val anchor = MarkdownPsiElementFactory.createHeader(file.project, 1, newName).anchorText
        checkNotNull(anchor)
        return super.prepareFileUpdateBatch(usages, anchor)
      }

      override fun prepareFileUpdate(usage: ModifiableRenameUsage, newName: String): Collection<FileOperation> {
        return listOf(FileOperation.modifyFile(file, StringOperation.replace(range, newName)))
      }
    }
  }
}
