// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.references

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.siblings
import com.intellij.util.Processor
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.index.MarkdownHeadersIndex
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.util.hasType

class MarkdownAnchorReferenceImpl internal constructor(
  private val myAnchor: String,
  private val myFileReference: PsiReference?,
  private val myPsiElement: PsiElement,
  private val myOffset: Int
) : MarkdownAnchorReference, PsiPolyVariantReferenceBase<PsiElement>(myPsiElement), EmptyResolveMessageProvider {
  private val file: PsiFile?
    get() = if (myFileReference != null) myFileReference.resolve() as? PsiFile else myPsiElement.containingFile.originalFile

  override fun getElement(): PsiElement = myPsiElement

  override fun getRangeInElement(): TextRange = TextRange(myOffset, myOffset + myAnchor.length)

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    if (myAnchor.isEmpty()) {
      val labelElement = myPsiElement.siblings(forward = false).firstOrNull { it.hasType(MarkdownElementTypes.LINK_LABEL) }
      val commentElement = myPsiElement.siblings(forward = true).firstOrNull { it.hasType(MarkdownElementTypes.LINK_COMMENT) }
      if (commentElement != null && labelElement?.text == "[//]") {
        return emptyArray()
      }
      return PsiElementResolveResult.createResults(myPsiElement)
    }

    val project = myPsiElement.project

    return PsiElementResolveResult.createResults(MarkdownAnchorReference.getPsiHeaders(project, canonicalText, file))
  }

  override fun getCanonicalText(): String = myAnchor

  override fun getVariants(): Array<Any> {
    val project = myPsiElement.project
    val list = ArrayList<String>()

    StubIndex.getInstance().getAllKeys(MarkdownHeadersIndex.KEY, project)
      .forEach { key ->
        StubIndex.getInstance().processElements(
          MarkdownHeadersIndex.KEY, key, project,
          file?.let { GlobalSearchScope.fileScope(it) },
          MarkdownHeader::class.java,
          Processor { list.add(MarkdownAnchorReference.dashed(key)) }
        )
      }

    return list.toTypedArray()
  }

  override fun getUnresolvedMessagePattern(): String {
    return when (file) {
      null -> MarkdownBundle.message("markdown.cannot.resolve.anchor.error.message", myAnchor)
      else -> MarkdownBundle.message("markdown.cannot.resolve.anchor.in.file.error.message", myAnchor, (file as PsiFile).name)
    }
  }
}
