// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereLucene.frontend

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

internal object SearchEverywhereLuceneFrontendBundle {
  private const val BUNDLE: @NonNls String = "messages.searchEverywhereLuceneFrontendBundle"

  private val INSTANCE = DynamicBundle(SearchEverywhereLuceneFrontendBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }

  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): Supplier<@Nls String> {
    return INSTANCE.getLazyMessage(key, *params)
  }
}
