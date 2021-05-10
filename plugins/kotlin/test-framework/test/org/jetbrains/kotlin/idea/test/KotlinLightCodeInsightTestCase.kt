// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

@file:Suppress("unused", "PropertyName")

package org.jetbrains.kotlin.idea.test

import org.jetbrains.kotlin.idea.test.KotlinTestUtils.*
import java.io.File

@Suppress("DEPRECATION")
@Deprecated("Use KotlinLightCodeInsightFixtureTestCase instead")
abstract class KotlinLightCodeInsightTestCase : com.intellij.testFramework.LightJavaCodeInsightTestCase() {
    open fun getTestDataDirectory(): File {
        val clazz = this::class.java
        val root = getTestsRoot(clazz)

        if (filesBasedTest) {
            return File(root)
        }

        val test = getTestDataFileName(clazz, name) ?: error("No @TestMetadata for ${clazz.name}")
        return File(root, test)
    }

    final override fun getTestDataPath(): String {
        return toSlashEndingDirPath(getTestDataDirectory().path)
    }

    protected open val filesBasedTest: Boolean = false

    protected fun testDataFile(fileName: String): File = File(testDataPath, fileName)

    protected fun testDataFile(): File = testDataFile(fileName())

    protected fun testPath(fileName: String = fileName()): String = testDataFile(fileName).toString()

    protected fun testPath(): String = testPath(fileName())

    protected open fun fileName(): String = getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

}