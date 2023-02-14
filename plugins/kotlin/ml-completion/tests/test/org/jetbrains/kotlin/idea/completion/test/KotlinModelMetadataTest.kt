// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.test

import org.jetbrains.kotlin.idea.mlCompletion.KotlinMLRankingProvider
import org.junit.Test

class KotlinModelMetadataTest {
    @Test
    fun testMetadataConsistent() = KotlinMLRankingProvider().assertModelMetadataConsistent()
}