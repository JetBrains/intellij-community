// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black.frontend

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

internal object PyBlackFrontendBundle {
  private const val BUNDLE = "messages.PyBlackFrontendBundle"
  private val INSTANCE = DynamicBundle(PyBlackFrontendBundle::class.java, BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String = INSTANCE.getMessage(key, *params)
}
