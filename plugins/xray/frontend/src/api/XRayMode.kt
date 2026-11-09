// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.api

import org.jetbrains.annotations.ApiStatus

/**
 * Enum representing different states of the X-Ray mode
 */
@ApiStatus.Experimental
enum class XRayMode {
  DISABLED, BASIC, EXTENDED
}
