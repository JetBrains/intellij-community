// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import junit.framework.TestCase

class FirAllTestsGeneratedTest : TestCase() {
    fun testAllTestsIsUpToDate() {
        generateK2Tests(isUpToDateCheck = true)
    }
}