// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.detector

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

class RefactoringDetectorBundle : DynamicBundle(BUNDLE) {
  companion object {
    const val BUNDLE = "messages.RefactoringDetectorBundle"

    private val INSTANCE = RefactoringDetectorBundle()

    @JvmStatic
    fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
      return INSTANCE.getMessage(key, *params)
    }

    @JvmStatic
    fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): Supplier<@Nls String> {
      return INSTANCE.getLazyMessage(key, *params)
    }
  }
}
