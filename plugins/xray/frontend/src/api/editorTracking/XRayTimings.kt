// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.api.editorTracking

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object XRayTimings {
  const val PEEK_HOLD_DELAY_MS: Int = 250

  const val TYPING_DELAY_MS: Int = 5000

  const val KEY_AUTOREPEAT_WINDOW_MS: Int = 50

  const val CARET_REFRESH_DELAY_MS: Int = 100
}
