// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused") // used at runtime

package org.jetbrains.kotlin.test

import com.intellij.testFramework.TestDataFile
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.jps.build.withSystemProperty
import org.jetbrains.kotlin.test.util.KtTestUtil
import java.io.File


object KotlinTestUtils {
    @JvmStatic
    fun tmpDirForTest(test: TestCase): File = KtTestUtil.tmpDirForTest(test.javaClass.simpleName, test.name)

    @JvmStatic
    fun assertEqualsToFile(expectedFile: File, actual: String) {
        KotlinTestUtils.assertEqualsToFile(expectedFile, actual)
    }

    @JvmStatic
    fun assertEqualsToFile(message: String, expectedFile: File, actual: String) {
        KotlinTestUtils.assertEqualsToFile(message, expectedFile, actual)
    }

    interface DoTest : KotlinTestUtils.DoTest

    @JvmStatic
    fun runTest(test: DoTest, testCase: TestCase, @TestDataFile testDataFile: String) {
        KotlinPluginLayout.kotlinc // to initialize dist
        withSystemProperty("jps.testData.js-ir-runtime", TestKotlinArtifacts.jsIrRuntimeDir.absolutePath) {
            KotlinTestUtils.runTest(test, testCase, testDataFile)
        }
    }

    @JvmStatic
    fun runTest(test: DoTest, targetBackend: TargetBackend, @TestDataFile testDataFile: String) {
        KotlinTestUtils.runTest(test, null, targetBackend, testDataFile)
    }

    @JvmStatic
    fun runTest(test: DoTest, testCase: TestCase, targetBackend: TargetBackend,  @TestDataFile testDataFile: String) {
        KotlinTestUtils.runTest(test, testCase, targetBackend, testDataFile)
    }
}
