// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.test

import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import java.io.File

object TestMetadataUtil {
    @JvmStatic
    fun getTestData(testClass: Class<*>): File? {
        val testRoot = getTestRoot(testClass) ?: return null
        val testMetadataAnnotationValue = getTestMetadata(testClass) ?: return null
        return File(testRoot, testMetadataAnnotationValue)
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
    fun <A: Annotation> getAnnotationValue(testClass: Class<*>, annotationClass: Class<A>, lookupEnclosingClass: Boolean = true): A? {
        var current = testClass
        if (lookupEnclosingClass) {
            while (true) {
                current = current.enclosingClass ?: break
            }
        }

        current.getAnnotation(annotationClass)?.let { return it }
        while (current != Any::class.java) {
            current = current.superclass
            current.getAnnotation(annotationClass)?.let { return it }
        }

        return null
    }

    @JvmStatic
    fun getTestRoot(testClass: Class<*>): File? =
        getAnnotationValue(testClass, TestRoot::class.java)?.let { KotlinRoot.DIR.resolve(it.value) }

    @JvmStatic
    fun getTestMetadata(testClass: Class<*>): String? =
        getAnnotationValue(testClass, TestMetadata::class.java, lookupEnclosingClass = false)?.let { return it.value }
}