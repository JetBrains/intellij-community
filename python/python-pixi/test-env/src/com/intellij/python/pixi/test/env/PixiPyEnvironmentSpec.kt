// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.test.env

import com.intellij.python.test.env.core.CacheKey
import com.intellij.python.test.env.core.PyEnvironmentSpec
import org.jetbrains.annotations.ApiStatus

/** Defines a Pixi-managed Python test environment. */
@ApiStatus.Internal
class PixiPyEnvironmentSpec(val pixiVersion: String) : PyEnvironmentSpec<PixiPyEnvironmentSpec>() {
  override fun toCacheKey(): CacheKey = buildCacheKey("pixi", "pixi:$pixiVersion")
}

/** Creates a specification for a Pixi-managed Python environment. */
@ApiStatus.Internal
fun pixiEnvironment(pixiVersion: String, block: PixiPyEnvironmentSpec.() -> Unit): PixiPyEnvironmentSpec =
  PixiPyEnvironmentSpec(pixiVersion).apply(block)
