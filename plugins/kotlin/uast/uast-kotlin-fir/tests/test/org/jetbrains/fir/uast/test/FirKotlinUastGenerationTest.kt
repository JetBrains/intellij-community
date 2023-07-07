// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.uast.test.kotlin.AbstractKotlinUastGenerationTest

class FirKotlinUastGenerationTest: AbstractKotlinUastGenerationTest() {
  override fun isFirPlugin(): Boolean {
    return true
  }

  fun `test method call generation with receiver`() {
    `do test method call generation with receiver`("1", "2")
  }

  override fun tearDown() {
    runAll(
      ThrowableRunnable { project.invalidateAllCachesForUastTests() },
      ThrowableRunnable { super.tearDown() },
    )
  }
}