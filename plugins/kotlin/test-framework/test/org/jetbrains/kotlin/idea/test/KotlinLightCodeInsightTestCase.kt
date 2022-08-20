// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import org.jetbrains.kotlin.idea.test.KotlinTestUtils.*
import java.io.File

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

    protected fun dataFile(fileName: String): File = File(testDataPath, fileName)

    protected fun dataFile(): File = dataFile(fileName())

    protected fun dataPath(fileName: String = fileName()): String = dataFile(fileName).toString()

    protected fun dataPath(): String = dataPath(fileName())

    protected open fun fileName(): String = getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

}