// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.references

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.index.MarkdownHeadersIndex
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderImpl

class MarkdownAnchorReferenceImpl internal constructor(private val myAnchor: String,
                                                       private val myFileReference: FileReference?,
                                                       private val myPsiElement: PsiElement,
                                                       private val myOffset: Int) : MarkdownAnchorReference, PsiPolyVariantReferenceBase<PsiElement>(
  myPsiElement), EmptyResolveMessageProvider {
  private val file: PsiFile?
    get() = if (myFileReference != null) myFileReference.resolve() as? PsiFile else myPsiElement.containingFile.originalFile

  override fun getElement(): PsiElement = myPsiElement

  override fun getRangeInElement(): TextRange = TextRange(myOffset, myOffset + myAnchor.length)

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    if (myAnchor.isEmpty()) return PsiElementResolveResult.createResults(myPsiElement)

    val project = myPsiElement.project

    return PsiElementResolveResult.createResults(MarkdownAnchorReference.getPsiHeaders(project, canonicalText, file))
  }

  override fun getCanonicalText(): String = myAnchor

  override fun getVariants(): Array<Any> {
    val project = myPsiElement.project
    val list = ArrayList<String>()

    StubIndex.getInstance().getAllKeys(MarkdownHeadersIndex.KEY, project)
      .forEach { key ->
        StubIndex.getInstance().processElements(MarkdownHeadersIndex.KEY, key, project,
                                                file?.let { GlobalSearchScope.fileScope(it) },
                                                MarkdownHeaderImpl::class.java,
                                                Processor { list.add(MarkdownAnchorReference.dashed(key)) }
        )
      }

    return list.toTypedArray()
  }

  override fun getUnresolvedMessagePattern(): String = if (file == null)
    MarkdownBundle.message("markdown.cannot.resolve.anchor.error.message", myAnchor)
  else
    MarkdownBundle.message("markdown.cannot.resolve.anchor.in.file.error.message", myAnchor, (file as PsiFile).name)
}
