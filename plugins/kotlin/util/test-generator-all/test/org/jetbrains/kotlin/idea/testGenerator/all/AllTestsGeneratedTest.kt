// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testGenerator.all

import junit.framework.TestCase
import org.jetbrains.kotlin.fe10.testGenerator.generateK1Tests

class AllTestsGeneratedTest : TestCase() {
    fun testAllTestsIsUpToDate() {
        generateK1Tests(isUpToDateCheck = true)
    }
}
