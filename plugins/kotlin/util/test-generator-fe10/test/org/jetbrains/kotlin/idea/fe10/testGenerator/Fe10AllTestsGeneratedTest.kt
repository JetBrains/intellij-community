// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fe10.testGenerator

import junit.framework.TestCase
import org.jetbrains.kotlin.fe10.testGenerator.generateK1Tests

class Fe10AllTestsGeneratedTest : TestCase() {
    fun testAllTestsIsUpToDate() {
        generateK1Tests(isUpToDateCheck = true)
    }
}