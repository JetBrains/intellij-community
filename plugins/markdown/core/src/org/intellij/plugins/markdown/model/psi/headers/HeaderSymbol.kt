package org.intellij.plugins.markdown.model.psi.headers

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.navigation.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.MarkdownIcons
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderContent
import org.intellij.plugins.markdown.lang.psi.util.childrenOfType
import org.intellij.plugins.markdown.model.psi.MarkdownSymbolWithUsages
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class HeaderSymbol(
  override val file: PsiFile,
  override val range: TextRange,
  val text: String,
  val anchorText: String
): MarkdownSymbolWithUsages, SearchTarget, RenameTarget, NavigatableSymbol, NavigationTarget {
  override fun createPointer(): Pointer<out HeaderSymbol> {
    val project = file.project
    val base = SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(file, range)
    return HeaderSymbolPointer(base, text, anchorText)
  }

  private class HeaderSymbolPointer(
    private val base: SmartPsiFileRange,
    private val text: String,
    private val anchorText: String
  ): Pointer<HeaderSymbol> {
    override fun dereference(): HeaderSymbol? {
      val file = base.containingFile ?: return null
      val range = base.range ?: return null
      return HeaderSymbol(file, TextRange.create(range), text, anchorText)
    }
  }

  override val targetName: String
    get() = text

  override val searchText: String
    get() = anchorText

  override val maximalSearchScope: SearchScope
    get() = GlobalSearchScope.allScope(file.project)

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler(anchorText)

  override fun presentation(): TargetPresentation {
    val virtualFile = file.containingFile.virtualFile
    val builder = TargetPresentation.builder(text).icon(MarkdownIcons.EditorActions.Header_level_up)
    val presentation = when (virtualFile) {
      null -> builder
      else -> when (virtualFile.fileType) {
        MarkdownFileType.INSTANCE -> builder.locationText(virtualFile.name, MarkdownIcons.MarkdownPlugin)
        else -> builder.locationText(virtualFile.name)
      }
    }
    return presentation.presentation()
  }

  override fun navigationRequest(): NavigationRequest? {
    val virtualFile = file.virtualFile?.takeIf { it.isValid } ?: return null
    return NavigationService.instance().sourceNavigationRequest(virtualFile, range.startOffset)
  }

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> {
    return listOf(this)
  }

  companion object {
    fun createPointer(header: MarkdownHeader): Pointer<HeaderSymbol>? {
      val headerContent = header.childrenOfType<MarkdownHeaderContent>().firstOrNull() ?: return null
      val anchorText = header.anchorText ?: return null
      val rangeInContentHolder = headerContent.nonWhitespaceRange
      val absoluteRange = rangeInContentHolder.shiftRight(headerContent.startOffset)
      val text = rangeInContentHolder.substring(headerContent.text)
      val file = header.containingFile
      val project = file.project
      val base = SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(file, absoluteRange)
      return HeaderSymbolPointer(base, text, anchorText)
    }
  }
}
