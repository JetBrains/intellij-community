// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.ide.rpc.DocumentPatchVersionAccessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

/** A serializable text range. */
@ApiStatus.Internal
@Serializable
data class MarkdownLivePreviewRange(
  @JvmField val startOffset: Int,
  @JvmField val endOffset: Int,
) {
  val length: Int
    get() = endOffset - startOffset

  val isEmpty: Boolean
    get() = startOffset >= endOffset
}

@ApiStatus.Internal
fun TextRange.toMarkdownLivePreviewRange(): MarkdownLivePreviewRange {
  return MarkdownLivePreviewRange(startOffset, endOffset)
}

@ApiStatus.Internal
fun MarkdownLivePreviewRange.toTextRange(): TextRange {
  return TextRange(startOffset, endOffset)
}

/** Identifies the document state that produced a spec set. */
@ApiStatus.Internal
@Serializable
data class MarkdownLivePreviewDocumentVersion(
  @JvmField val patchVersion: DocumentPatchVersion?,
  @JvmField val localModificationStamp: Long,
  @JvmField val elementsHash: Int = 0,
) {
  fun matchesDocument(other: MarkdownLivePreviewDocumentVersion): Boolean {
    if (patchVersion != null || other.patchVersion != null) {
      return patchVersion == other.patchVersion
    }
    return localModificationStamp == other.localModificationStamp
  }

  fun matches(document: Document, project: Project): Boolean {
    return matchesDocument(capture(document, project))
  }

  fun withElements(elements: List<MarkdownLivePreviewSpec>, sourceHash: Int = 0): MarkdownLivePreviewDocumentVersion {
    return copy(elementsHash = 31 * elements.hashCode() + sourceHash)
  }

  companion object {
    @JvmStatic
    fun capture(document: Document, project: Project): MarkdownLivePreviewDocumentVersion {
      return MarkdownLivePreviewDocumentVersion(
        DocumentPatchVersionAccessor.getDocumentVersion(document, project),
        document.modificationStamp,
      )
    }
  }
}

/** A live-preview decoration and the source range it reveals when touched. */
@ApiStatus.Internal
@Serializable
sealed interface MarkdownLivePreviewSpec {
  val range: MarkdownLivePreviewRange

  /** Conceals one or more markup ranges, optionally replacing them with a placeholder. */
  @Serializable
  data class Conceal(
    override val range: MarkdownLivePreviewRange,
    val conceals: List<MarkdownLivePreviewRange>,
  ) : MarkdownLivePreviewSpec

  /** Conceals a full logical line and paints it as a horizontal rule. */
  @Serializable
  data class HorizontalRule(override val range: MarkdownLivePreviewRange) : MarkdownLivePreviewSpec

  /** Conceals complete logical lines and paints a local image. */
  @Serializable
  data class Image(
    override val range: MarkdownLivePreviewRange,
    val destination: String,
    val source: VirtualFileId? = null,
  ) : MarkdownLivePreviewSpec

  /** Replaces a list marker with a depth-aware bullet placeholder. */
  @Serializable
  data class Bullet(
    override val range: MarkdownLivePreviewRange,
    val concealRange: MarkdownLivePreviewRange,
    val placeholderText: String,
  ) : MarkdownLivePreviewSpec
}

/** Everything live preview wants to hide in one state of a document. */
@ApiStatus.Internal
@Serializable
data class MarkdownLivePreviewSpecSet(
  @JvmField val documentVersion: MarkdownLivePreviewDocumentVersion,
  @JvmField val elements: List<MarkdownLivePreviewSpec>,
) {
  @Transient
  @JvmField
  val maxElementLength: Int = elements.maxOfOrNull { it.range.length } ?: 0

  /** Indices of specs whose ranges intersect the closed interval [[start], [end]]. */
  fun intersecting(start: Int, end: Int, into: MutableSet<Int>) {
    // `elements` is sorted by start offset, so every element that intersects [start, end] starts at or
    // before `end`, and - since it also has to reach `start` - no earlier than `start - maxElementLength`.
    var index = firstElementAfter(end)
    val lowestStart = start - maxElementLength
    while (index-- > 0) {
      val element = elements[index]
      if (element.range.startOffset < lowestStart) break
      if (element.range.endOffset >= start) {
        into.add(index)
      }
    }
  }

  /** Index of the first element starting strictly after [offset], i.e., the exclusive upper bound. */
  private fun firstElementAfter(offset: Int): Int {
    var low = 0
    var high = elements.size
    while (low < high) {
      val mid = (low + high) ushr 1
      if (elements[mid].range.startOffset > offset) high = mid else low = mid + 1
    }
    return low
  }
}
