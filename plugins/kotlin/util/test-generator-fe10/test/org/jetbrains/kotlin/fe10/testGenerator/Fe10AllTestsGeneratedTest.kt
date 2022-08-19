// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fe10.testGenerator

import junit.framework.TestCase

class Fe10AllTestsGeneratedTest : TestCase() {
    fun testAllTestsIsUpToDate() {
        generateK1Tests(isUpToDateCheck = true)
    }
}