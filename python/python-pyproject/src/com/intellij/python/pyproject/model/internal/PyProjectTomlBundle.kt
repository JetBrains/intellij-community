// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal

import com.intellij.DynamicBundle
import com.intellij.python.pyproject.PyProjectToml
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

internal object PyProjectTomlBundle : DynamicBundle(PyProjectToml::class.java, BUNDLE) {

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String =
    getMessage(key, *params)

  fun messagePointer(
    @PropertyKey(resourceBundle = BUNDLE) key: @PropertyKey(resourceBundle = BUNDLE) String,
    vararg params: Any,
  ): Supplier<String> = getLazyMessage(key, *params)
}

private const val BUNDLE = "messages.PyProjectTomlBundle"
