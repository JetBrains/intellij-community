// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * Public because `python-sdk` hosts the SDK-aware half of this module's API (`PyToolSdkExt.kt`) and reports its errors
 * from these messages. Widening the bundle keeps each string with the module that owns the concept, rather than
 * orphaning existing translations by moving keys into another bundle.
 */
object PyToolsBundle {
  private const val BUNDLE = "messages.PyToolsBundle"

  private val INSTANCE = DynamicBundle(PyToolsBundle::class.java, BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }
}
