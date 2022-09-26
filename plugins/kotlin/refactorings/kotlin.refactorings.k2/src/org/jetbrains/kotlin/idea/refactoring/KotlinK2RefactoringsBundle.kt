// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

object KotlinK2RefactoringsBundle {
  private const val BUNDLE_FQN: @NonNls String = "messages.KotlinK2RefactoringsBundle"
  private val BUNDLE = DynamicBundle(KotlinK2RefactoringsBundle::class.java, BUNDLE_FQN)
    
  fun message(key: @PropertyKey(resourceBundle = BUNDLE_FQN) String, vararg params: Any): @Nls String {
    return BUNDLE.getMessage(key, *params)
  }

  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE_FQN) String, vararg params: Any): Supplier<String> {
    return BUNDLE.getLazyMessage(key, *params)
  }
}