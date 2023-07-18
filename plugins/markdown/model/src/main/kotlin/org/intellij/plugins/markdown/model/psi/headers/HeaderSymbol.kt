package org.intellij.plugins.markdown.model.psi.headers

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
import com.intellij.psi.util.childrenOfType
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.MarkdownIcons
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderContent
import org.intellij.plugins.markdown.model.psi.withLocationIn
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class HeaderSymbol(
  override val file: PsiFile,
  override val range: TextRange,
  override val text: @NlsSafe String,
  override val anchorText: @NlsSafe String
): MarkdownHeaderSymbol, NavigationTarget, SearchTarget, RenameTarget {
  override fun createPointer(): Pointer<out HeaderSymbol> {
    return createPointer(file, range, text, anchorText)
  }

  override fun computePresentation(): TargetPresentation {
    return presentation()
  }

  override fun navigationRequest(): NavigationRequest? {
    return NavigationRequest.sourceNavigationRequest(file, range)
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
    val builder = TargetPresentation.builder(text).icon(MarkdownIcons.EditorActions.Header_level_up)
    return builder.withLocationIn(file).presentation()
  }

  companion object {
    fun createPointer(header: MarkdownHeader): Pointer<HeaderSymbol>? {
      val headerContent = header.childrenOfType<MarkdownHeaderContent>().firstOrNull() ?: return null
      val anchorText = header.anchorText ?: return null
      val rangeInContentHolder = headerContent.nonWhitespaceRange
      val absoluteRange = rangeInContentHolder.shiftRight(headerContent.startOffset)
      val text = rangeInContentHolder.substring(headerContent.text)
      val file = header.containingFile
      return createPointer(file, absoluteRange, text, anchorText)
    }

    fun createPointer(file: PsiFile, absoluteRange: TextRange, text: String, anchorText: String): Pointer<HeaderSymbol> {
      return Pointer.fileRangePointer(file, absoluteRange) { restoredFile, restoredRange ->
        HeaderSymbol(restoredFile, restoredRange, text, anchorText)
      }
    }
  }
}
