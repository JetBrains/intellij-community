// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.ide.rpc.DocumentPatchVersionAccessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/** Holds the live-preview state of an editor. Live preview is off until [setLivePreviewSupport] turns it on. */
private val LIVE_PREVIEW_KEY: Key<MutableStateFlow<Boolean>> = Key.create("markdown.live.preview")

private fun Editor.livePreviewState(): MutableStateFlow<Boolean> {
  return (this as UserDataHolderEx).getOrCreateUserData(LIVE_PREVIEW_KEY) { MutableStateFlow(false) }
}

@ApiStatus.Experimental
fun Editor.enableLivePreviewSupport() {
  setLivePreviewSupport(true)
}

/** Turns Markdown live preview on or off for this editor. */
@ApiStatus.Experimental
fun Editor.setLivePreviewSupport(enabled: Boolean) {
  livePreviewState().value = enabled
}

@ApiStatus.Experimental
fun Editor.supportsLivePreview(): Boolean {
  return getUserData(LIVE_PREVIEW_KEY)?.value == true
}

/** Emits the live-preview state of this editor each time [setLivePreviewSupport] changes it. */
@ApiStatus.Internal
fun Editor.livePreviewSupportFlow(): StateFlow<Boolean> {
  return livePreviewState()
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

  /** Conceals blockquote markers and paints a vertical rule across the blockquote. */
  @Serializable
  data class BlockQuote(
    override val range: MarkdownLivePreviewRange,
    val markerRanges: List<MarkdownLivePreviewRange>,
  ) : MarkdownLivePreviewSpec

  /** Conceals a full logical line and paints it as a horizontal rule. */
  @Serializable
  data class HorizontalRule(override val range: MarkdownLivePreviewRange) : MarkdownLivePreviewSpec

  /**
   * Renders a top-level ATX heading over its full logical line.
   * The frontend uses [level] to select the HTML heading element.
   * [html] is inline HTML. Each text run in it is a span with its `md-src-pos` source range.
   * The source ranges are relative to the start of [range].
   */
  @Serializable
  data class Heading(
    override val range: MarkdownLivePreviewRange,
    val level: Int,
    val html: String,
  ) : MarkdownLivePreviewSpec

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

  /** Replaces a list marker with a depth-aware bullet placeholder until a caret touches the marker. */
  @Serializable
  data class Bullet(
    override val range: MarkdownLivePreviewRange,
    val placeholderText: String,
  ) : MarkdownLivePreviewSpec

  /** Replaces a task marker with a checkbox until a caret touches the marker. */
  @Serializable
  data class TaskCheckbox(
    override val range: MarkdownLivePreviewRange,
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
