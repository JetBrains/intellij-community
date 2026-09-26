// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.typeEngine.frontend

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

object TypeEngineFrontendBundle {
  private const val BUNDLE = "messages.TypeEngineFrontendBundle"
  private val INSTANCE = DynamicBundle(TypeEngineFrontendBundle::class.java, BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String =
    INSTANCE.getMessage(key, *params)
}
