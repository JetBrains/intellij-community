// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.api

/**
 * Visibility stages for inlay hint options
 */
enum class InlayHintStage {
  /** The inlay hint should be shown regardless of the X-Ray mode. */
  ALWAYS,

  /** Shown when X-Ray mode is [XRayMode.BASIC] or [XRayMode.EXTENDED]. */
  ESSENTIAL,

  /** Shown only when X-Ray mode is [XRayMode.EXTENDED]. */
  ADDITIONAL,

  /** Never shown. */
  DISABLED;

  /**
   * How visible this stage is, from `0` (least visible, [DISABLED]) to the most visible ([ALWAYS]).
   * Matches the inlay-settings visibility slider and the order of the "change inlay stage" menu.
   * Larger rank means "shown in more X-Ray modes" (see [isVisibleIn]).
   */
  val visibilityRank: Int get() = entries.size - 1 - ordinal

  /**
   * A function that defines the visibility semantics for different Inlay Hint stages.
   * Depending on the given mode tells whether the inlay hints with given stage shall be visible in the editor.
   */
  fun isVisibleIn(mode: XRayMode): Boolean = when (this) {
    ALWAYS -> true
    ESSENTIAL -> mode == XRayMode.BASIC || mode == XRayMode.EXTENDED
    ADDITIONAL -> mode == XRayMode.EXTENDED
    DISABLED -> false
  }

  val isEnabled: Boolean get() = this != DISABLED
}
