// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.IdentifiersTestBase
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.uast.test.common.asRefNames
import java.io.File

abstract class AbstractKotlinIdentifiersTest : AbstractKotlinUastTest(), IdentifiersTestBase {

    private fun getTestFile(testName: String, ext: String) =
        File(File(TEST_KOTLIN_MODEL_DIR, testName).canonicalPath + '.' + ext)

    override fun getIdentifiersFile(testName: String): File = getTestFile(testName, "identifiers.txt")

    override fun check(testName: String, file: UFile) {
        super.check(testName, file)
        assertEqualsToFile("refNames", getTestFile(testName, "refNames.txt"), file.asRefNames())
    }
}
