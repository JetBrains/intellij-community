// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testGenerator.all

import junit.framework.TestCase
import org.jetbrains.kotlin.fe10.testGenerator.generateK1Tests
import org.jetbrains.kotlin.fir.testGenerator.generateK2Tests
import org.jetbrains.tools.model.updater.KotlinTestsDependenciesUtil

class AllTestsGeneratedTest : TestCase() {
    fun testAllTestsIsUpToDate() {
        KotlinTestsDependenciesUtil.updateChecksum(isUpToDateCheck = true)
        generateK1Tests(isUpToDateCheck = true)
        generateK2Tests(isUpToDateCheck = true)
    }
}
