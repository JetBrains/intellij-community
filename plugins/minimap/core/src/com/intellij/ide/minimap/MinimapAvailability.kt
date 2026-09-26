// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MinimapAvailability {
  private const val REGISTRY_KEY: String = "ide.minimap.available"

  fun isAvailable(): Boolean {
    return Registry.`is`(REGISTRY_KEY)
  }
}
