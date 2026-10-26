// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.PyInternalExecApi
import org.jetbrains.annotations.ApiStatus

/**
 * Support unified local/eel-based development approach
 */
@PyInternalExecApi
@get:ApiStatus.Internal
val eelNativeMode: Boolean get() = Registry.`is`("python.eel.native")
