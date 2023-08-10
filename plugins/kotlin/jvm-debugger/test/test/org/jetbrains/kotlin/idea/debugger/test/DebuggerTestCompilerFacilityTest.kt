// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.runner.RunWith

@SuppressWarnings("all")
@TestRoot("jvm-debugger/test")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("testData")
@RunWith(JUnit3RunnerWithInners::class)
class DebuggerTestCompilerFacilityTest : AbstractKotlinSteppingTest() {
    @TestMetadata("testWithDiagnosticErrors.kt")
    fun testTestWithDiagnosticErrors() {
        val testDataFilePath = "testData/testWithDiagnosticErrors.kt"
        assertThrows(RuntimeException::class.java, "inferred type is String but Int was expected") {
            KotlinTestUtils.runTest(this::doStepIntoTest, this, testDataFilePath)
        }
    }
}