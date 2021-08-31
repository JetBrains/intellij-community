/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.fir.uast

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.fir.uast.env.kotlin.AbstractFirUastTest
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.KotlinRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastApiTestBase
import org.junit.runner.RunWith

@RunWith(JUnit3RunnerWithInners::class)
open class FirUastApiTest : AbstractFirUastTest() {
    override val isFirUastPlugin: Boolean = true

    override val basePath = KotlinRoot.DIR_PATH.resolve("uast")

    override fun check(filePath: String, file: UFile) {
        // Bogus
    }

    @TestMetadata("../uast-kotlin/testData")
    @TestDataPath("\$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners::class)
    class Legacy : FirUastApiTest(), UastApiTestBase {
        override val isFirUastPlugin: Boolean = true

        override fun check(filePath: String, file: UFile) {
            // Bogus
        }

        @TestMetadata("SAM.kt")
        fun testSAM() {
            doCheck("uast-kotlin/testData/SAM.kt", ::checkCallbackForSAM)
        }

        @TestMetadata("Simple.kt")
        fun testSimple() {
            doCheck("uast-kotlin/testData/Simple.kt", ::checkCallbackForSimple)
        }
    }
}
