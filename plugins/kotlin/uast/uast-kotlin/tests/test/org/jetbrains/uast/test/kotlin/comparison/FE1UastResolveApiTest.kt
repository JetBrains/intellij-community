// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin.comparison

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastResolveApiTestBase
import org.jetbrains.uast.test.kotlin.env.AbstractFE1UastTest
import org.junit.runner.RunWith

@RunWith(JUnit3RunnerWithInners::class)
class FE1UastResolveApiTest : AbstractFE1UastTest() {
    override fun check(testName: String, file: UFile) {
        // Bogus
    }

    @TestMetadata("uast-kotlin-fir/testData/declaration")
    @TestDataPath("/")
    @RunWith(JUnit3RunnerWithInners::class)
    class Declaration : AbstractFE1UastTest(), UastResolveApiTestBase {
        override var testDataDir = KotlinRoot.DIR.resolve("uast/uast-kotlin-fir/testData/declaration")

        override val isFirUastPlugin: Boolean = false

        override fun check(testName: String, file: UFile) {
            // Bogus
        }

        @TestMetadata("doWhile.kt")
        fun testDoWhile() {
            doTest("doWhile", ::checkCallbackForDoWhile)
        }

        @TestMetadata("if.kt")
        fun testIf() {
            doTest("if", ::checkCallbackForIf)
        }

        @TestMetadata("retention.kt")
        fun testRetention() {
            doTest("retention", ::checkCallbackForRetention)
        }
    }

    @TestMetadata("uast-kotlin-fir/testData/type")
    @TestDataPath("/")
    @RunWith(JUnit3RunnerWithInners::class)
    class Type : AbstractFE1UastTest(), UastResolveApiTestBase {
        override var testDataDir = KotlinRoot.DIR.resolve("uast/uast-kotlin-fir/testData/type")

        override val isFirUastPlugin: Boolean = false

        override fun check(testName: String, file: UFile) {
            // Bogus
        }

        @TestMetadata("threadSafe.kt")
        fun testThreadSafe() {
            doTest("threadSafe", ::checkThreadSafe)
        }
    }

    @TestMetadata("uast-kotlin/tests/testData")
    @TestDataPath("/")
    @RunWith(JUnit3RunnerWithInners::class)
    class Legacy : AbstractFE1UastTest(), UastResolveApiTestBase {
        override var testDataDir = KotlinRoot.DIR.resolve("uast/uast-kotlin/tests/testData")

        override val isFirUastPlugin: Boolean = false

        override fun check(testName: String, file: UFile) {
            // Bogus
        }

        @TestMetadata("MethodReference.kt")
        fun testMethodReference() {
            doTest("MethodReference", ::checkCallbackForMethodReference)
        }

        @TestMetadata("Imports.kt")
        fun testImports() {
            doTest("Imports", ::checkCallbackForImports)
        }

        @TestMetadata("ReceiverFun.kt")
        fun testReceiverFun() {
            doTest("ReceiverFun", ::checkCallbackForReceiverFun)
        }

        @TestMetadata("Resolve.kt")
        fun testResolve() {
            doTest("Resolve", ::checkCallbackForResolve)
        }
    }
}
