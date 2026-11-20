// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Selects the elements that Markdown live preview renders in an editor.
 * A prohibited element keeps its source visible. Other specs can still apply.
 */
@ApiStatus.Experimental
@Serializable
data class MarkdownLivePreviewConfiguration(
  /** The element names and code fence languages that live preview must not render. */
  val prohibited: Set<String>,
) {
  companion object {
    /** The `"images"` value prohibits image rendering, including images inside headings. */
    @ApiStatus.Experimental
    const val IMAGES: String = "images"

    /** The `"headings"` value prohibits heading rendering, including Setext underlines. */
    @ApiStatus.Experimental
    const val HEADINGS: String = "headings"

    /**
     * The `"blockQuotes"` value prohibits block quote and alert rendering.
     * It also keeps quoted headings in source form.
     */
    @ApiStatus.Experimental
    const val BLOCK_QUOTES: String = "blockQuotes"

    /**
     * The `"mermaid"` value prohibits the Mermaid code fence view.
     * The fence markers and the code font still render.
     */
    @ApiStatus.Experimental
    const val MERMAID: String = "mermaid"

    /** The default Markdown live preview configuration. It renders all supported elements. */
    @ApiStatus.Experimental
    @JvmField
    val DEFAULT_LIVE_PREVIEW_CONFIGURATION: MarkdownLivePreviewConfiguration = MarkdownLivePreviewConfiguration(
      prohibited = emptySet(),
    )
  }
}
