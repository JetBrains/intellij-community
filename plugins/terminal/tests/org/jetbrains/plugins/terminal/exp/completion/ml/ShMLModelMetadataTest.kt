// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion.ml

import org.junit.Test

class ShMLModelMetadataTest {
  @Test
  fun testModelMetadataConsistent() {
    ShMLRankingProvider().assertModelMetadataConsistent()
  }
}
