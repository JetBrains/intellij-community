// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin.common

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.resolvableWithTargets
import org.jetbrains.uast.test.env.kotlin.assertEqualsToFile
import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_DIR
import java.io.File

interface ResolveEverythingTestBase {
    fun getTestFile(testName: String, ext: String) =
        File(File(TEST_KOTLIN_MODEL_DIR, testName).canonicalPath + '.' + ext)

    fun check(testName: String, file: UFile) {
        assertEqualsToFile("resolved", getTestFile(testName, "resolved.txt"), file.resolvableWithTargets())
    }
}
