// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.test

import java.io.File
import org.jetbrains.kotlin.test.KotlinRoot

object TestMetadataUtil {
    @JvmStatic
    fun getTestData(testClass: Class<*>): File? {
        val testRoot = getTestRoot(testClass) ?: return null
        val testMetadataAnnotation = testClass.getAnnotation(TestMetadata::class.java) ?: return null
        return File(testRoot, testMetadataAnnotation.value)
    }

    @JvmStatic
    fun getTestDataPath(testClass: Class<*>): String {
        val path = (getTestData(testClass) ?: KotlinRoot.DIR).absolutePath
        return if (path.endsWith(File.separator)) path else path + File.separator
    }

    @JvmStatic
    fun getFile(testClass: Class<*>, path: String): File {
        return File(getTestData(testClass), path)
    }

    @JvmStatic
    fun getTestRoot(testClass: Class<*>): File? {
        var current = testClass
        while (true) {
            current = current.enclosingClass ?: break
        }

        val testRootAnnotation = current.getAnnotation(TestRoot::class.java) ?: return null
        return KotlinRoot.DIR.resolve(testRootAnnotation.value)
    }
}