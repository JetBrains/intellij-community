package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.intellij.plugins.markdown.MarkdownIcons
import org.intellij.plugins.markdown.lang.MarkdownLanguageUtils.isMarkdownLanguage
import org.intellij.plugins.markdown.model.psi.MarkdownSourceNavigationTarget
import org.intellij.plugins.markdown.model.psi.MarkdownSymbolInsideInjection
import org.intellij.plugins.markdown.model.psi.headers.MarkdownHeaderSymbol
import org.intellij.plugins.markdown.model.psi.withLocationIn
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class HtmlAnchorSymbol(
  /**
   * Expected to be a host Markdown file.
   */
  override val file: PsiFile,
  /**
   * File-level range. It should be safe to just pass element text range from the injected HTML file,
   * since HTML is a template language for Markdown.
   */
  override val range: TextRange,
  override val anchorText: @NlsSafe String
): MarkdownHeaderSymbol, MarkdownSymbolInsideInjection, SearchTarget, NavigatableSymbol {
  init {
    check(file.language.isMarkdownLanguage()) { "Should be created only on host Markdown files" }
  }

  override fun createPointer(): Pointer<out HtmlAnchorSymbol> {
    return createPointer(file, range, anchorText)
  }

  override val maximalSearchScope: SearchScope
    get() = GlobalSearchScope.allScope(file.project)

  override val text: String
    get() = anchorText

  override fun presentation(): TargetPresentation {
    val builder = TargetPresentation.builder("#$text").icon(MarkdownIcons.EditorActions.Link)
    return builder.withLocationIn(file).containerText("Anchor").presentation()
  }

  override val searchText: String
    get() = anchorText

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler(anchorText)

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> {
    val virtualFile = file.virtualFile ?: return emptyList()
    return listOf(HtmlAnchorNavigationTarget(virtualFile, range.startOffset))
  }

  private inner class HtmlAnchorNavigationTarget(file: VirtualFile, offset: Int): MarkdownSourceNavigationTarget(file, offset) {
    override fun computePresentation(): TargetPresentation {
      return this@HtmlAnchorSymbol.presentation()
    }
  }

  companion object {
    private fun createPointer(file: PsiFile, range: TextRange, anchorText: @NlsSafe String): Pointer<HtmlAnchorSymbol> {
      return Pointer.fileRangePointer(file, range) { restoredFile, restoredRange ->
        HtmlAnchorSymbol(restoredFile, restoredRange, anchorText)
      }
    }
  }
}
