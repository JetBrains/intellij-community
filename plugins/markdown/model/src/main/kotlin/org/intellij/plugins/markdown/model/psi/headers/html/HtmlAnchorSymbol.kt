package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownIcons
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
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
): MarkdownHeaderSymbol, MarkdownSymbolInsideInjection, NavigationTarget, SearchTarget {
  init {
    check(file.language.isMarkdownLanguage()) { "Should be created only on host Markdown files. File was $file." }
  }

  override fun createPointer(): Pointer<out HtmlAnchorSymbol> {
    return createPointer(file, range, anchorText)
  }

  override fun computePresentation(): TargetPresentation {
    return presentation()
  }

  override fun navigationRequest(): NavigationRequest? {
    return NavigationRequest.sourceNavigationRequest(file, range)
  }

  override val maximalSearchScope: SearchScope
    get() = GlobalSearchScope.allScope(file.project)

  override val text: String
    get() = anchorText

  override fun presentation(): TargetPresentation {
    val builder = TargetPresentation.builder("#$text").icon(MarkdownIcons.EditorActions.Link)
    return builder.withLocationIn(file)
      .containerText(MarkdownBundle.message("markdown.html.anchor.symbol.presentation.container.text"))
      .presentation()
  }

  override val searchText: String
    get() = anchorText

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler(anchorText)

  companion object {
    private fun createPointer(file: PsiFile, range: TextRange, anchorText: @NlsSafe String): Pointer<HtmlAnchorSymbol> {
      return Pointer.fileRangePointer(file, range) { restoredFile, restoredRange ->
        HtmlAnchorSymbol(restoredFile, restoredRange, anchorText)
      }
    }
  }
}
