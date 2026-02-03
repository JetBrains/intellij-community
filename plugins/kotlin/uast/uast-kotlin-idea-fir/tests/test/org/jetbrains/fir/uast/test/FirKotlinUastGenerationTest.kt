// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.uast.test.kotlin.AbstractKotlinUastGenerationTest

class FirKotlinUastGenerationTest: AbstractKotlinUastGenerationTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun `test method call generation with receiver`() {
    `do test method call generation with receiver`("1", "2")
  }

  override fun tearDown() {
    runAll(
      { project.invalidateAllCachesForUastTests() },
      { super.tearDown() },
    )
  }
}