/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.comparasion

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.KotlinRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastApiTestBase
import org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.env.kotlin.AbstractFE1UastTest
import org.junit.runner.RunWith

@RunWith(JUnit3RunnerWithInners::class)
class FE1UastApiTest : AbstractFE1UastTest() {
    override fun check(testName: String, file: UFile) {
        // Bogus
    }

    @TestMetadata("uast-kotlin/testData")
    @TestDataPath("/")
    class Legacy : AbstractFE1UastTest(), UastApiTestBase {
        override var testDataDir = KotlinRoot.DIR_PATH.resolve("uast/uast-kotlin/testData").toFile()

        override val isFirUastPlugin: Boolean = false

        override fun check(testName: String, file: UFile) {
            // Bogus
        }

        @TestMetadata("SAM.kt")
        fun testSAM() {
            doTest("SAM", ::checkCallbackForSAM)
        }
    }
}
