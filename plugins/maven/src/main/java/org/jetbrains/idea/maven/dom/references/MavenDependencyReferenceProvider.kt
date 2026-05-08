// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.references

import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ArrayUtilRt
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.maven.completion.MavenDependencySearchService
import org.jetbrains.idea.maven.plugins.api.MavenSoftAwareReferenceProvider

/**
 * Adds references to string like "groupId:artifactId:version"
 */
open class MavenDependencyReferenceProvider : PsiReferenceProvider(), MavenSoftAwareReferenceProvider {
  private var mySoft = true

  var isCanHasVersion: Boolean = true

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val range = ElementManipulators.getValueTextRange(element)

    val text = range.substring(element.getText())

    val firstDelim = text.indexOf(':')

    if (firstDelim == -1) {
      return arrayOf(
        GroupReference(element, range, mySoft)
      )
    }

    val secondDelim = if (this.isCanHasVersion) text.indexOf(':', firstDelim + 1) else -1

    val start = range.getStartOffset()

    if (secondDelim == -1) {
      return arrayOf<PsiReference>(
        GroupReference(element, TextRange(start, start + firstDelim), mySoft),
        ArtifactReference(
          text.substring(0, firstDelim),
          element, TextRange(start + firstDelim + 1, range.getEndOffset()), mySoft
        )
      )
    }

    var lastDelim = text.indexOf(':', secondDelim + 1)
    if (lastDelim == -1) {
      lastDelim = text.length
    }

    return arrayOf<PsiReference>(
      GroupReference(element, TextRange(start, start + firstDelim), mySoft),

      ArtifactReference(
        text.substring(0, firstDelim),
        element, TextRange(start + firstDelim + 1, start + secondDelim), mySoft
      ),

      VersionReference(
        text.substring(0, firstDelim), text.substring(firstDelim + 1, secondDelim),
        element, TextRange(start + secondDelim + 1, start + lastDelim), mySoft
      )
    )
  }

  override fun setSoft(soft: Boolean) {
    mySoft = soft
  }

  private class GroupReference(element: PsiElement, range: TextRange?, soft: Boolean) :
    PsiReferenceBase<PsiElement?>(element, range, soft) {
    override fun resolve(): PsiElement? {
      return null
    }

    override fun getVariants(): Array<Any?> {
      return runBlockingCancellable { MavenDependencySearchService.getInstance(element.getProject()).getGroupIds("") }.toTypedArray()
    }
  }

  class ArtifactReference(private val myGroupId: String, element: PsiElement, range: TextRange, soft: Boolean) :
    PsiReferenceBase<PsiElement?>(element, range, soft) {
    override fun resolve(): PsiElement? {
      return null
    }

    override fun getVariants(): Array<Any?> {
      if (myGroupId.isBlank()) return ArrayUtilRt.EMPTY_OBJECT_ARRAY

      return runBlockingCancellable { MavenDependencySearchService.getInstance(element.getProject()).getArtifactIds(myGroupId) }.toTypedArray()
    }
  }

  class VersionReference(
    private val myGroupId: String,
    private val myArtifactId: String,
    element: PsiElement,
    range: TextRange,
    soft: Boolean,
  ) : PsiReferenceBase<PsiElement?>(element, range, soft) {
    override fun resolve(): PsiElement? {
      return null
    }

    override fun getVariants(): Array<Any?> {
      if (myGroupId.isBlank() || myArtifactId.isBlank()) {
        return ArrayUtilRt.EMPTY_OBJECT_ARRAY
      }
      return runBlockingCancellable { MavenDependencySearchService.getInstance(element.getProject()).getVersions(myGroupId, myArtifactId) }.toTypedArray()
    }
  }
}
