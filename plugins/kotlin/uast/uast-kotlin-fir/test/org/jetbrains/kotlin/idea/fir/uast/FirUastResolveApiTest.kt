// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.uast

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.uast.*
import org.jetbrains.kotlin.idea.fir.uast.env.kotlin.AbstractFirUastTest
import org.jetbrains.kotlin.test.KotlinRoot
import org.jetbrains.uast.test.common.kotlin.UastResolveApiTestBase
import org.junit.runner.RunWith

@RunWith(JUnit3RunnerWithInners::class)
open class FirUastResolveApiTest : AbstractFirUastTest() {
    override val isFirUastPlugin: Boolean = true

    override val basePath = KotlinRoot.DIR_PATH.resolve("uast")

    override fun check(filePath: String, file: UFile) {
        // Bogus
    }

    @TestMetadata("plugins/uast-kotlin-fir/testData/declaration")
    @TestDataPath("\$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners::class)
    class Declaration : FirUastResolveApiTest(), UastResolveApiTestBase {
        override val isFirUastPlugin: Boolean = true

        override fun check(filePath: String, file: UFile) {
            // Bogus
        }

        @TestMetadata("doWhile.kt")
        fun testDoWhile() {
            doCheck("uast-kotlin-fir/testData/declaration/doWhile.kt", ::checkCallbackForDoWhile)
        }

        @TestMetadata("if.kt")
        fun testIf() {
            doCheck("uast-kotlin-fir/testData/declaration/if.kt", ::checkCallbackForIf)
        }

        // TODO: once call is supported, test labeledExpression.kt for labeled this and super
    }

    @TestMetadata("../uast-kotlin/testData")
    @TestDataPath("\$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners::class)
    class Legacy : FirUastResolveApiTest(), UastResolveApiTestBase {
        override val isFirUastPlugin: Boolean = true

        override fun check(filePath: String, file: UFile) {
            // Bogus
        }

        @TestMetadata("MethodReference.kt")
        fun testMethodReference() {
            doCheck("uast-kotlin/testData/MethodReference.kt", ::checkCallbackForMethodReference)
        }

        @TestMetadata("Imports.kt")
        fun testImports() {
            doCheck("uast-kotlin/testData/Imports.kt", ::checkCallbackForImports)
        }

        fun testReceiverFun() {
            doCheck("uast-kotlin/testData/ReceiverFun.kt", ::checkCallbackForReceiverFun)
        }
    }
}
