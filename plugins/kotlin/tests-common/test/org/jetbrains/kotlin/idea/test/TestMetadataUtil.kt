// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.test

import com.intellij.testFramework.common.BazelTestUtil
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import java.io.File
import kotlin.io.path.Path

object TestMetadataUtil {
    private val kotlinJvmDebuggerTestDataRoot = Path("jvm-debugger", "test", "testData")
    private val debuggerJvmAdvancedKotlinTestDataRoot = Path(
        "..", "..", "..",
        "plugins", "debugger", "jvm-advanced", "jvm.advanced.kotlin", "intellij.debugger.jvm.advanced.kotlin.tests", "testData"
    )
    private val supportedBazelTestDataRoots = listOf(
        kotlinJvmDebuggerTestDataRoot to { TestKotlinArtifacts.kotlinJvmDebuggerTestData },
        debuggerJvmAdvancedKotlinTestDataRoot to { TestKotlinArtifacts.debuggerJvmAdvancedKotlinTestData },
    )

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
     * - Currently supports only Kotlin debugger test data rooted under:
     *   - "jvm-debugger/test/testData"
     *   - "../../../plugins/debugger/jvm-advanced/jvm.advanced.kotlin/intellij.debugger.jvm.advanced.kotlin.tests/testData"
     *   For supported roots, the method computes the relative path under that subtree and returns a File resolved against
     *   the matching artifact from [TestKotlinArtifacts].
     *
     * Note:
     * - This limitation is temporary: only explicitly wired Kotlin debugger test-data roots are supported for now.
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
        // Temporary limitation: only explicitly wired Kotlin debugger test-data roots are supported.
        // TODO: Generalize to support Bazel-provided test data without a hardcoded root allowlist.
        val supportedRoot = supportedBazelTestDataRoots.firstOrNull { (root, _) -> normalizedPathToResolve.startsWith(root) }
        if (supportedRoot != null) {
            val (root, artifactProvider) = supportedRoot
            val pathToResolveRelativeTestData = root.relativize(normalizedPathToResolve)
            return File(artifactProvider().resolve(pathToResolveRelativeTestData).toString())
        }
        val resolvedFrom = "TestRoot='${testRoot.value}' + path='${pathToResolve}' => '$normalizedPathToResolve'"
        val supportedRoots = supportedBazelTestDataRoots.joinToString { (root, _) -> "'$root'" }
        throw IllegalStateException(
            "Running tests via Bazel is not yet supported for ${testClass.name}.\n" +
            "Resolved test data path: $resolvedFrom\n" +
            "Only paths under $supportedRoots are currently supported."
        )
    }
}
