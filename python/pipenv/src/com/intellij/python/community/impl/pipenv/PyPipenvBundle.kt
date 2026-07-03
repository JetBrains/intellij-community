// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.pipenv

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

internal object PyPipenvBundle {
  private const val BUNDLE = "messages.PyPipenvBundle"

  private val INSTANCE = DynamicBundle(PyPipenvBundle::class.java, BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }
}
