// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.render

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Controls which token spans participate in the minimap token-filler layer.
 *
 * This hook can be used to suppress language-specific markers from the default token layer
 * (for example notebook cell separators like `#%%`) while rendering them in a dedicated layer.
 *
 * Register via `com.intellij.minimapTokenRenderPolicy`.
 * The first applicable policy wins.
 */
@ApiStatus.OverrideOnly
interface MinimapTokenRenderPolicy {
  fun isApplicable(editor: Editor): Boolean = true

  /**
   * @return `true` if [startOffset, endOffset) should be rendered by token filler.
   */
  fun shouldRenderTokenSpan(
    editor: Editor,
    document: Document,
    lineStartOffset: Int,
    lineEndOffset: Int,
    startOffset: Int,
    endOffset: Int,
  ): Boolean = true

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MinimapTokenRenderPolicy> =
      ExtensionPointName("com.intellij.minimapTokenRenderPolicy")

    fun forEditor(editor: Editor): MinimapTokenRenderPolicy {
      return EP_NAME.extensionList.firstOrNull { it.isApplicable(editor) } ?: DefaultMinimapTokenRenderPolicy
    }
  }
}

private object DefaultMinimapTokenRenderPolicy : MinimapTokenRenderPolicy
