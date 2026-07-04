// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.k2

import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import java.io.File

class K2JavaToKotlinConverterInMemoryMultiFileParityTest : AbstractK2JavaToKotlinConverterMultiFileTest() {
    private fun testDataPath(relativePath: String): String = File(KotlinRoot.DIR, "j2k/shared/tests/testData/$relativePath").path

    
    fun testProtectedVisibility() {
        doTest(testDataPath("multiFile/ProtectedVisibility"))
    }

    fun testToObject() {
        doTest(testDataPath("multiFile/ToObject"))
    }
}
