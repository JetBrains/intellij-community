// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.kotlin.common.TypesTestBaseDuplicate
import java.io.File

abstract class AbstractKotlinTypesTest : AbstractKotlinUastTest(), TypesTestBaseDuplicate {
    private fun getTestFile(testName: String, ext: String) =
            File(File(TEST_KOTLIN_MODEL_DIR, testName).canonicalPath + '.' + ext)

    override fun getTypesFile(testName: String) = getTestFile(testName, "types.txt")
}