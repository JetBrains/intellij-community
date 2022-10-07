package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.find.usages.api.PsiUsage
import com.intellij.mermaid.lang.psi.symbol.MermaidPsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.refactoring.rename.api.*
import com.intellij.util.Query
import com.intellij.util.text.StringOperation

@Suppress("UnstableApiUsage")
class IdentifierRenameUsageSearcher : RenameUsageSearcher {
  override fun collectSearchRequests(parameters: RenameUsageSearchParameters): Collection<Query<out RenameUsage>> {
    val target = parameters.target
    if (target !is UnresolvedIdentifierSymbol) {
      return emptyList()
    }
    val searchText = target.searchText.takeIf { it.isNotEmpty() } ?: return emptyList()
    val usages =
      IdentifierSymbolUsageSearcher.buildSearchRequest(parameters.project, target, searchText, parameters.searchScope)
    val otherDeclarations = IdentifierSymbolUsageSearcher.buildDeclarationsSearchRequest(
      parameters.project,
      target,
      searchText,
      parameters.searchScope
    )
    val selfUsage = IdentifierSymbolUsageSearcher.buildDirectTargetQuery(
      MermaidPsiUsage.create(
        target.file,
        target.range,
        declaration = true
      )
    )
    val modifiedUsages = usages.mapping { IdentifierModifiableRenameUsage(it.file, it.range) }
    val modifiedOtherDeclarations = otherDeclarations.mapping { IdentifierModifiableRenameUsage(it.file, it.range) }
    val modifiedSelfUsage = selfUsage.mapping { ModifiableRenameUsageWrapper(it) }
    return listOf(modifiedUsages, modifiedOtherDeclarations, modifiedSelfUsage)
  }

  private class ModifiableRenameUsageWrapper(
    usage: PsiUsage
  ) : PsiModifiableRenameUsage by PsiModifiableRenameUsage.defaultPsiModifiableRenameUsage(usage) {
    override val declaration = true

    override val fileUpdater = updater

    private class Updater : ModifiableRenameUsage.FileUpdater by idFileRangeUpdater()

    companion object {
      private val updater = Updater()
    }
  }

  private class IdentifierModifiableRenameUsage(
    override val file: PsiFile,
    override val range: TextRange
  ) : PsiModifiableRenameUsage {
    override fun createPointer(): Pointer<out PsiModifiableRenameUsage> {
      return IdentifierPsiModifiableRenameUsagePointer(file, range)
    }

    override val declaration: Boolean = false

    override val fileUpdater: ModifiableRenameUsage.FileUpdater
      get() = IdentifierFileUpdater(file, range)

    private class IdentifierFileUpdater(
      private val file: PsiFile,
      private val range: TextRange
    ) : ModifiableRenameUsage.FileUpdater {
      override fun prepareFileUpdate(usage: ModifiableRenameUsage, newName: String): Collection<FileOperation> {
        return listOf(FileOperation.modifyFile(file, StringOperation.replace(range, newName)))
      }
    }

    private class IdentifierPsiModifiableRenameUsagePointer(
      file: PsiFile,
      range: TextRange
    ) : Pointer<IdentifierModifiableRenameUsage> {
      private val rangePointer =
        SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, range)

      override fun dereference(): IdentifierModifiableRenameUsage? {
        val file = rangePointer.element ?: return null
        val range = rangePointer.range?.let(TextRange::create) ?: return null
        return IdentifierModifiableRenameUsage(file, range)
      }
    }
  }
}
