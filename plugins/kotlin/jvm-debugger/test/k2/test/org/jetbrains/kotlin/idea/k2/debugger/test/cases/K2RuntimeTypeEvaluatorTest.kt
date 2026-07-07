// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.debugger.test.AbstractK2RuntimeTypeEvaluatorTest
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.runner.RunWith

@TestRoot("jvm-debugger/test/k2")
@TestDataPath("\$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners::class)
@TestMetadata("../testData/runtimeType")
class K2RuntimeTypeEvaluatorTest : AbstractK2RuntimeTypeEvaluatorTest() {
    @TestMetadata("runtimeType.kt")
    fun testRuntimeType() {
        doTest("../testData/runtimeType/runtimeType.kt")
    }

    @TestMetadata("intersectionType.kt")
    fun testIntersectionType() {
        doTest("../testData/runtimeType/intersectionType.kt")
    }

    @TestMetadata("localClass.kt")
    fun testLocalClass() {
        doTest("../testData/runtimeType/localClass.kt")
    }

    @TestMetadata("intersectionLocal.kt")
    fun testIntersectionLocal() {
        doTest("../testData/runtimeType/intersectionLocal.kt")
    }
}
