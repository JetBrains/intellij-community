package org.intellij.plugins.markdown.injection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal class CodeFenceHighlightInfoFilter: HighlightInfoFilter {
  override fun accept(highlightInfo: HighlightInfo, psiFile: PsiFile?): Boolean {
    if (psiFile == null) {
      return true
    }
    val project = psiFile.project
    val manager = InjectedLanguageManager.getInstance(project)
    val topLevelFile = manager.getTopLevelFile(psiFile) ?: return true
    if (topLevelFile.fileType == MarkdownFileType.INSTANCE && manager.getInjectionHost(psiFile) is MarkdownCodeFence) {
      if (highlightInfo.severity !in internalSeverities) {
        return MarkdownSettings.getInstance(project).showProblemsInCodeBlocks
      }
    }
    return true
  }

  companion object {
    private val internalSeverities = setOf(
      HighlightInfoType.SYMBOL_TYPE_SEVERITY,
      HighlightInfoType.INJECTED_FRAGMENT_SYNTAX_SEVERITY,
      HighlightInfoType.INJECTED_FRAGMENT_SEVERITY,
      HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY,
      HighlightInfoType.HIGHLIGHTED_REFERENCE_SEVERITY,
      HighlightSeverity.TEXT_ATTRIBUTES
    )
  }
}
