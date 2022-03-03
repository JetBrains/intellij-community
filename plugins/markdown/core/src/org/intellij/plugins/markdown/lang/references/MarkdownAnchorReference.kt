package org.intellij.plugins.markdown.lang.references

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.stubs.StubIndex
import org.intellij.plugins.markdown.lang.index.MarkdownHeadersIndex
import java.util.*

interface MarkdownAnchorReference : PsiPolyVariantReference {
  companion object {
    fun getPsiHeaders(project: Project, text: String, psiFile: PsiFile?): Collection<PsiElement> {
      // optimization: trying to find capitalized header
      val suggestedHeader = StringUtil.replace(text, "-", " ")
      var headers: Collection<PsiElement> = MarkdownHeadersIndex.collectFileHeaders(StringUtil.capitalize(suggestedHeader), project, psiFile)
      if (headers.isNotEmpty()) return headers

      headers = MarkdownHeadersIndex.collectFileHeaders(StringUtil.capitalizeWords(suggestedHeader, true), project, psiFile)
      if (headers.isNotEmpty()) return headers

      // header search
      headers = StubIndex.getInstance().getAllKeys(MarkdownHeadersIndex.KEY, project)
        .filter { dashed(it) == text }
        .flatMap { MarkdownHeadersIndex.collectFileHeaders(it, project, psiFile) }

      return headers
    }

    fun dashed(it: String): String =
      it.lowercase(Locale.getDefault())
        .trimStart()
        .replace(Regex("[^\\w\\- ]"), "")
        .replace(" ", "-")
  }
}
