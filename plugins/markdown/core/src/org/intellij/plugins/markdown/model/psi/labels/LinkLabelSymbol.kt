package org.intellij.plugins.markdown.model.psi.labels

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
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.MarkdownIcons
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDefinition
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownShortReferenceLink
import org.intellij.plugins.markdown.model.psi.MarkdownSymbolWithUsages
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class LinkLabelSymbol(
  override val file: PsiFile,
  override val range: TextRange,
  val text: String
): MarkdownSymbolWithUsages, SearchTarget, RenameTarget, NavigatableSymbol, NavigationTarget {
  override fun createPointer(): Pointer<out LinkLabelSymbol> {
    val project = file.project
    val base = SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(file, range)
    return LinkLabelPointer(base, text)
  }

  private class LinkLabelPointer(private val base: SmartPsiFileRange, private val text: String): Pointer<LinkLabelSymbol> {
    override fun dereference(): LinkLabelSymbol? {
      val file = base.containingFile ?: return null
      val range = base.range ?: return null
      return LinkLabelSymbol(file, TextRange.create(range), text)
    }
  }

  override val targetName: String
    get() = text

  override val maximalSearchScope: SearchScope?
    get() = GlobalSearchScope.fileScope(file)

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler(text)

  override val searchText: String
    get() = text

  override fun presentation(): TargetPresentation {
    return TargetPresentation.builder(text).icon(MarkdownIcons.EditorActions.Link).presentation()
  }

  override fun navigationRequest(): NavigationRequest? {
    val virtualFile = file.virtualFile?.takeIf { it.isValid } ?: return null
    return NavigationService.instance().sourceNavigationRequest(virtualFile, range.startOffset)
  }

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> {
    return listOf(this)
  }

  companion object {
    fun createPointer(label: MarkdownLinkLabel): Pointer<LinkLabelSymbol>? {
      val text = label.text
      val rangeInElement = TextRange(0, text.length)
      val absoluteRange = rangeInElement.shiftRight(label.startOffset)
      val textInElement = rangeInElement.substring(text)
      val file = label.containingFile
      val project = file.project
      val base = SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(file, absoluteRange)
      return LinkLabelPointer(base, textInElement)
    }

    val MarkdownLinkLabel.isDeclaration: Boolean
      get() = parentOfType<MarkdownLinkDefinition>() != null

    val MarkdownLinkLabel.isShortLink: Boolean
      get() = parentOfType<MarkdownShortReferenceLink>() != null
  }
}
