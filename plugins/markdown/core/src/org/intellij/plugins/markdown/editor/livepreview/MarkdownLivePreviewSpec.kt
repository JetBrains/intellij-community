// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.ide.rpc.DocumentPatchVersionAccessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import kotlinx.serialization.Serializable
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings
import org.jetbrains.annotations.ApiStatus

/**
 * Enables Markdown live preview for an editor. Always enabled for [EditorKind.MAIN_EDITOR].
 */
private val LIVE_PREVIEW_KEY: Key<Boolean> = Key.create("markdown.live.preview")

@ApiStatus.Experimental
fun Editor.enableLivePreviewSupport() {
  putUserData(LIVE_PREVIEW_KEY, true)
}

@ApiStatus.Experimental
fun Editor.supportsLivePreview(): Boolean {
  return getUserData(LIVE_PREVIEW_KEY) ?: (editorKind == EditorKind.MAIN_EDITOR)
}

fun Editor.isLivePreviewEnabled(): Boolean {
  return MarkdownApplicationSettings.getInstance().enableLivePreview && supportsLivePreview()
}

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

  /**
   * The loaded file behind an [Image]: its modification [stamp] and its intrinsic size in logical pixels.
   * A new [stamp] means new pixels behind the same destination.
   */
  @Serializable
  data class ImageSource(
    val stamp: Long,
    val width: Int,
    val height: Int,
  )

  /**
   * Replaces the source of a local image with [placeholderText] and paints the image below its line.
   */
  @Serializable
  data class Image(
    override val range: MarkdownLivePreviewRange,
    val destination: String,
    val placeholderText: String,
    val source: ImageSource? = null,
  ) : MarkdownLivePreviewSpec

  /** Replaces a list marker with a depth-aware bullet placeholder. */
  @Serializable
  data class Bullet(
    override val range: MarkdownLivePreviewRange,
    val concealRange: MarkdownLivePreviewRange,
    val placeholderText: String,
  ) : MarkdownLivePreviewSpec

  /** Replaces a task marker with a checkbox until a caret touches its first logical line. */
  @Serializable
  data class TaskCheckbox(
    override val range: MarkdownLivePreviewRange,
    val concealRange: MarkdownLivePreviewRange,
    val checked: Boolean,
  ) : MarkdownLivePreviewSpec
}

/** Everything live preview wants to hide in one state of a document. */
@ApiStatus.Internal
@Serializable
data class MarkdownLivePreviewSpecSet(
  @JvmField val documentVersion: MarkdownLivePreviewDocumentVersion,
  @JvmField val elements: List<MarkdownLivePreviewSpec>,
)
