// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.test

import com.intellij.testFramework.common.BazelTestUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import java.io.File
import kotlin.io.path.Path

object TestMetadataUtil {
    @JvmStatic
    fun getTestData(testClass: Class<*>): File? {
        val testMetadataAnnotationValue = getTestMetadata(testClass) ?: return null
        if (BazelTestUtil.isUnderBazelTest) {
            return resolvePathInBazelProvidedTestData(testClass, testMetadataAnnotationValue)
        } else {
            val testRoot = getTestRoot(testClass) ?: return null
            return File(testRoot, testMetadataAnnotationValue)
        }
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

    /**
     * Resolves a Bazel-specific test data file path for the given [testClass] and [pathToResolve].
     *
     * Behavior:
     * - Requires the presence of a [TestRoot] annotation on [testClass] (or its hierarchy via [getAnnotationValue]).
     *   The [TestRoot.value] is treated as a base directory. The provided [pathToResolve] is resolved against this
     *   base and then normalized.
     * - Currently supports only test data paths that are under the "jvm-debugger/test/testData" subtree.
     *   For such paths, the method computes the relative path under that subtree and returns a File resolved against
     *   [TestKotlinArtifacts.kotlinJvmDebuggerTestData].
     *
     * Note:
     * - This limitation is temporary: jvm-debugger tests are the first test module being migrated to Bazel, so only
     *   this subtree is wired up for now. A general solution without this restriction will be introduced later.
     *
     * Exceptions:
     * - Throws [IllegalStateException] if [TestRoot] is not found on [testClass].
     * - Throws [IllegalStateException] if running the test via Bazel is not supported for the resolved path.
     *
     * @param testClass the test class whose @TestRoot determines the base for resolution
     * @param pathToResolve a path relative to the @TestRoot base that points to the requested test data
     * @return a File pointing to the test data within the supported Bazel test data artifact location
     */
    @JvmStatic
    fun resolvePathInBazelProvidedTestData(testClass: Class<*>, pathToResolve: String): File {
        val testRoot: TestRoot? = getAnnotationValue(testClass, TestRoot::class.java)
        if (testRoot == null) {
            throw IllegalStateException("@TestRoot annotation was not found on " + testClass.name)
        }
        val normalizedPathToResolve = Path(testRoot.value).resolve(pathToResolve).normalize()
        // Temporary limitation: only jvm-debugger/test/testData is supported during the initial Bazel migration.
        // TODO: Generalize to support other test modules once they are migrated to Bazel-provided test data.
        val jvmDebuggerTestData = Path("jvm-debugger", "test", "testData")
        if (normalizedPathToResolve.startsWith(jvmDebuggerTestData)) {
            val pathToResolveRelativeTestData = jvmDebuggerTestData.relativize(normalizedPathToResolve);
            return TestKotlinArtifacts.kotlinJvmDebuggerTestData.resolve(pathToResolveRelativeTestData.toString())
        } else {
            val resolvedFrom = "TestRoot='${testRoot.value}' + path='${pathToResolve}' => '$normalizedPathToResolve'"
            val supportedRoot = jvmDebuggerTestData.toString()
            throw IllegalStateException(
                "Running tests via Bazel is not yet supported for ${testClass.name}.\n" +
                "Resolved test data path: $resolvedFrom\n" +
                "Only paths under '$supportedRoot' are currently supported."
            )
        }
    }
}