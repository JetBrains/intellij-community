// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.util.registry.Registry

/**
 * Support unified local/eel-based development approach
 */
internal val eelNativeMode: Boolean get() = Registry.`is`("python.eel.native")
