// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.kmp

import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.DoTest
import java.nio.file.Paths

internal object KMPTestRunner {
    @JvmStatic
    fun run(testDataFile: String, testCase: DoTest, test: KMPTest) {
        val platform = test.testPlatform
        tryIgnoring(testDataFile, platform, testCase)
    }

    private fun tryIgnoring(testDataFile: String, platform: KMPTestPlatform, testCase: DoTest) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            Paths.get(testDataFile),
            "// IGNORE_PLATFORM_${platform.directiveName}"
        ) { testCase(testDataFile) }
    }
}