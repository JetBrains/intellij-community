// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.completion.ml

import org.jetbrains.plugins.terminal.block.completion.ml.ShMLRankingProvider
import org.junit.Test

internal class ShMLModelMetadataTest {
  @Test
  fun testModelMetadataConsistent() {
    ShMLRankingProvider().assertModelMetadataConsistent()
  }
}
