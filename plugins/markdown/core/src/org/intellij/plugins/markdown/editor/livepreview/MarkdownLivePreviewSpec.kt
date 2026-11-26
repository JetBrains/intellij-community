// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.ide.rpc.patchVersion
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

/**
 * Holds the live-preview configuration of an editor.
 * Live preview is off while it is null, until [setLivePreviewEnabledness] turns it on.
 */
private val LIVE_PREVIEW_KEY: Key<MutableStateFlow<MarkdownLivePreviewConfiguration?>> = Key.create("markdown.live.preview")

private fun Editor.livePreviewState(): MutableStateFlow<MarkdownLivePreviewConfiguration?> {
  return (this as UserDataHolderEx).getOrCreateUserData(LIVE_PREVIEW_KEY) { MutableStateFlow(null) }
}

/**
 * Turns Markdown live preview on or off for this editor.
 * While it is on, live preview renders the elements that [configuration] does not prohibit.
 */
@ApiStatus.Experimental
@JvmOverloads
fun Editor.setLivePreviewEnabledness(
  enabled: Boolean,
  configuration: MarkdownLivePreviewConfiguration = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION,
) {
  livePreviewState().value = if (enabled) configuration else null
}

@ApiStatus.Experimental
fun Editor.isLivePreviewEnabled(): Boolean {
  return livePreviewConfiguration() != null
}

internal fun Editor.livePreviewConfiguration(): MarkdownLivePreviewConfiguration? {
  return getUserData(LIVE_PREVIEW_KEY)?.value
}

/** Emits the live-preview configuration of this editor each time it changes. Null means that live preview is off. */
@ApiStatus.Internal
fun Editor.livePreviewConfigurationFlow(): StateFlow<MarkdownLivePreviewConfiguration?> {
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
        document.patchVersion(project),
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

  /**
   * Hides the glyph of a blockquote marker until a caret touches [range]. The marker keeps its width.
   * The [range] covers the run of markers on the marker line. Only spaces and tabs separate the markers of a run.
   * Thus one touch reveals all markers of the run.
   * While the marker is concealed, it paints a vertical rule over [ruleRange].
   */
  @Serializable
  data class BlockQuote(
    override val range: MarkdownLivePreviewRange,
    /** The `>` character with the hidden glyph. */
    val markerRange: MarkdownLivePreviewRange,
    /** Starts at the marker. Ends at the start of the next marker line of the blockquote, or at the end of the marker line. */
    val ruleRange: MarkdownLivePreviewRange,
    /** The rule takes the title color of this alert type. A regular blockquote has no alert type. */
    val alertType: AlertType? = null,
  ) : MarkdownLivePreviewSpec

  @Serializable
  enum class AlertType { NOTE, TIP, IMPORTANT, WARNING, CAUTION }

  /** Conceals a full logical line and paints it as a horizontal rule. */
  @Serializable
  data class HorizontalRule(override val range: MarkdownLivePreviewRange) : MarkdownLivePreviewSpec

  /**
   * Renders a top-level ATX or Setext heading over its full logical line.
   * The range of a Setext heading excludes its underline. A separate [HorizontalRule] covers the underline.
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

  /** Tells why the editor shows a placeholder instead of [Image]. */
  @Serializable
  enum class ImageFailure { NOT_FOUND, OUTSIDE_PROJECT, UNREADABLE, TOO_LARGE, UNTRUSTED }

  /**
   * Replaces the source of an image with [placeholderText] and paints the image below its line.
   * An image with a [failure] has no [source]. The editor paints a placeholder that shows the reason of [failure].
   */
  @Serializable
  data class Image(
    override val range: MarkdownLivePreviewRange,
    val destination: String,
    val placeholderText: String,
    val isBrowserOwned: Boolean,
    val source: ImageSource? = null,
    val failure: ImageFailure? = null,
  ) : MarkdownLivePreviewSpec

  /** Renders an object under the code fence, if the [language] is supported */
  @Serializable
  data class CodeFence(
    override val range: MarkdownLivePreviewRange,
    val language: String,
    val source: String,
  ) : MarkdownLivePreviewSpec

  /**
   * Lays out the code of a closed code fence like a block of the HTML preview.
   * The editor pads the language and each code line on the left, so the language is the header of the code.
   * The editor pads nothing while the opening marker at [markerStart] shows its source.
   * [languageStart] is the start of the language, or null for a fence without a language.
   * Each offset of [codeStarts] is the start of the code on one line. The offsets leave out the line prefixes of a list or a blockquote.
   */
  @Serializable
  data class CodeFenceLayout(
    override val range: MarkdownLivePreviewRange,
    val markerStart: Int,
    val languageStart: Int?,
    val codeStarts: List<Int>,
  ) : MarkdownLivePreviewSpec

  /** Shows [codeRanges] in the code font. The ranges leave out the line prefixes of a list or a blockquote. */
  @Serializable
  data class CodeFont(
    override val range: MarkdownLivePreviewRange,
    val codeRanges: List<MarkdownLivePreviewRange>,
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

  /**
   * Widens the indent of one logical line of a list item. The [range] is empty and sits at the line start.
   * [depth] is the nesting depth of the list item that holds the first non-blank character of the line, at least 1.
   * The frontend adds the top-level list indent, and one indent unit per level above the first.
   */
  @Serializable
  data class ListIndent(
    override val range: MarkdownLivePreviewRange,
    val depth: Int,
  ) : MarkdownLivePreviewSpec
}

/** Everything live preview wants to hide in one state of a document. */
@ApiStatus.Internal
@Serializable
data class MarkdownLivePreviewSpecSet(
  @JvmField val documentVersion: MarkdownLivePreviewDocumentVersion,
  @JvmField val elements: List<MarkdownLivePreviewSpec>,
)
