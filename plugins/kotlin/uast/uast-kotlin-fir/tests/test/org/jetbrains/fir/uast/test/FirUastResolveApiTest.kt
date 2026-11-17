// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import com.intellij.testFramework.TestDataPath
import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastResolveApiTestBase
import org.junit.runner.RunWith
import java.nio.file.Path

@RunWith(JUnit3RunnerWithInners::class)
@TestRoot("uast")
abstract class FirUastResolveApiTest : AbstractFirUastTest() {

    override val testBasePath: Path
        get() = testDataDirectory.toPath()
    override fun check(filePath: String, file: UFile) {
        // Bogus
    }

    private val whitelist : Set<String> = setOf(
        // TODO: resolve to inline and stdlib
        "Resolve.kt",
    )

    override fun isExpectedToFail(filePath: String, fileContent: String): Boolean {
        return filePath in whitelist || super.isExpectedToFail(filePath, fileContent)
    }

    @TestMetadata("uast-kotlin-fir/tests/testData/declaration")
    @TestDataPath("\$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners::class)
    class Declaration : FirUastResolveApiTest(), UastResolveApiTestBase {

        @TestMetadata("doWhile.kt")
        fun testDoWhile() {
            doCheck("doWhile.kt", ::checkCallbackForDoWhile)
        }

        @TestMetadata("if.kt")
        fun testIf() {
            doCheck("if.kt", ::checkCallbackForIf)
        }

        // TODO: once call is supported, test labeledExpression.kt for labeled this and super

        @TestMetadata("retention.kt")
        fun testRetention() {
            doCheck("retention.kt", ::checkCallbackForRetention)
        }
    }

    @TestMetadata("uast-kotlin-fir/tests/testData/type")
    @TestDataPath("\$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners::class)
    class Type : FirUastResolveApiTest(), UastResolveApiTestBase {
        @TestMetadata("threadSafe.kt")
        fun testThreadSafe() {
            doCheck("threadSafe.kt", ::checkThreadSafe)
        }
    }

    @TestMetadata("uast-kotlin/tests/testData")
    @TestDataPath("\$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners::class)
    class Legacy : FirUastResolveApiTest(), UastResolveApiTestBase {
        @TestMetadata("MethodReference.kt")
        fun testMethodReference() {
            doCheck("MethodReference.kt", ::checkCallbackForMethodReference)
        }

        @TestMetadata("Imports.kt")
        fun testImports() {
            doCheck("Imports.kt", ::checkCallbackForImports)
        }

        fun testReceiverFun() {
            doCheck("ReceiverFun.kt", ::checkCallbackForReceiverFun)
        }

        @TestMetadata("Resolve.kt")
        fun testResolve() {
            doCheck("Resolve.kt", ::checkCallbackForResolve)
        }
    }
}
