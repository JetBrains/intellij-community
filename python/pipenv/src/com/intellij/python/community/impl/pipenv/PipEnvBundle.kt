// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.pipenv

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@ApiStatus.Internal
object PipEnvBundle {
  private const val BUNDLE_FQN: @NonNls String = "messages.PipEnvBundle"
  private val BUNDLE = DynamicBundle(PipEnvBundle::class.java, BUNDLE_FQN)

  fun message(
    key: @PropertyKey(resourceBundle = BUNDLE_FQN) String,
    vararg params: Any,
  ): @Nls String =
    BUNDLE.getMessage(key, *params)
}
