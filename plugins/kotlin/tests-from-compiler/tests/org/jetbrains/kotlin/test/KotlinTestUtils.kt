// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused") // used at runtime

package org.jetbrains.kotlin.test

import com.intellij.testFramework.TestDataFile
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX_PATH
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.compiler.configuration.isRunningFromSources
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.jps.build.withSystemProperty
import org.jetbrains.kotlin.test.util.KtTestUtil
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists


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
        KotlinPluginLayout.kotlincPath
        if (isRunningFromSources) {  // for IDEA Trunk / Kotlin IDE Trunk / Kotlin JPS Tests Trunk, and IDEA Trunk / Kotlin IDE Trunk / Kotlin JPS Tests Bundled
            // some tests take Kotlin compiler from jps.kotlin.home but don't restore it explicitly (e.g. org.jetbrains.kotlin.jps.build.KotlinJpsBuildTest), copy it  -- TODO: https://youtrack.jetbrains.com/issue/KTIJ-38387
            val kotlincDistForIdeFromSources = KOTLIN_DIST_LOCATION_PREFIX_PATH.resolve("kotlinc-dist-for-ide-from-sources")
            val jpsKotlinHome = System.getProperty("jps.kotlin.home")?.let(::Path)
            if (kotlincDistForIdeFromSources.isDirectory() && jpsKotlinHome?.notExists() == true) kotlincDistForIdeFromSources.toFile().copyRecursively(jpsKotlinHome.toFile())
        }
        withSystemProperty("jps.testData.js-ir-runtime", TestKotlinArtifacts.jsIrRuntimeDir.absolutePathString()) {
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
