// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testGenerator.all

import org.jetbrains.kotlin.fir.testGenerator.generateK2Tests
import org.jetbrains.tools.model.updater.KotlinTestsDependenciesUtil
import org.junit.jupiter.api.Test

class AllTestsGeneratedTest {
    @Test
    fun testAllTestsIsUpToDate() {
        KotlinTestsDependenciesUtil.updateChecksums(isUpToDateCheck = true)
        generateK2Tests(isUpToDateCheck = true)
    }
}
