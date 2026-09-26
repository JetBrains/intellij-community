// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.testGenerator

import junit.framework.TestCase
import org.jetbrains.kotlin.fir.testGenerator.generateK2Tests

class FirAllTestsGeneratedTest : TestCase() {
    fun testAllTestsIsUpToDate() {
        generateK2Tests(isUpToDateCheck = true)
    }
}