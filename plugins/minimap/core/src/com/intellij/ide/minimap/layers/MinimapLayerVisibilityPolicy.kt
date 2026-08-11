// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layers

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Controls visibility of minimap layers per editor.
 *
 * Register via `com.intellij.minimapLayerVisibilityPolicy`.
 * Applicable policies are evaluated in registration order. Any policy can veto a layer by returning `false`.
 */
@ApiStatus.OverrideOnly
interface MinimapLayerVisibilityPolicy {
  fun isApplicable(editor: Editor): Boolean = true

  fun isLayerEnabled(editor: Editor, layerId: MinimapLayerId): Boolean = true

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MinimapLayerVisibilityPolicy> =
      ExtensionPointName("com.intellij.minimapLayerVisibilityPolicy")

    fun isLayerEnabled(editor: Editor, layerId: MinimapLayerId): Boolean {
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        if (!policy.isLayerEnabled(editor, layerId)) return false
      }
      return true
    }
  }
}
