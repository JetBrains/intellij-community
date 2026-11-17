// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.test

import com.intellij.testFramework.common.BazelTestUtil
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString

object TestMetadataUtil {
    private val kotlinJvmDebuggerTestDataRoot = Path("jvm-debugger", "test", "testData")
    private val kotlinJpsGraphTestDataRoot = Path("jps", "graphImplementationTests", "testData")
    private val debuggerJvmAdvancedKotlinTestDataRoot = Path(
        "..", "..", "..",
        "plugins", "debugger", "jvm-advanced", "jvm.advanced.kotlin", "intellij.debugger.jvm.advanced.kotlin.tests", "testData"
    )
    private val supportedBazelTestDataRoots = listOf(
        kotlinJvmDebuggerTestDataRoot to { TestKotlinArtifacts.kotlinJvmDebuggerTestData },
        debuggerJvmAdvancedKotlinTestDataRoot to { TestKotlinArtifacts.debuggerJvmAdvancedKotlinTestData },
        kotlinJpsGraphTestDataRoot to {
            TestKotlinArtifacts.getKotlinDepsByLabel("@community//plugins/kotlin/jps/graphImplementationTests:testData")
        },
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
     * Resolves [pathToResolve] relative to [TestRoot] using test data declared in the Bazel target.
     */
    @JvmStatic
    fun resolvePathInBazelProvidedTestData(testClass: Class<*>, pathToResolve: String): File {
        val testRoot: TestRoot? = getAnnotationValue(testClass, TestRoot::class.java)
        if (testRoot == null) {
            throw IllegalStateException("@TestRoot annotation was not found on " + testClass.name)
        }
        val normalizedPathToResolve = Path(testRoot.value).resolve(pathToResolve).normalize()
        val supportedRoot = supportedBazelTestDataRoots.firstOrNull { (root, _) -> normalizedPathToResolve.startsWith(root) }
        if (supportedRoot != null) {
            val (root, artifactProvider) = supportedRoot
            val pathToResolveRelativeTestData = root.relativize(normalizedPathToResolve)
            return File(artifactProvider().resolve(pathToResolveRelativeTestData).toString())
        }
        val testDataIndex = normalizedPathToResolve.indexOfFirst { it.toString() == "testData" }
        check(testDataIndex >= 0 && !normalizedPathToResolve.startsWith("..") && !normalizedPathToResolve.isAbsolute) {
            "Cannot resolve test data for ${testClass.name}: $normalizedPathToResolve"
        }
        val testDataRoot = normalizedPathToResolve.subpath(0, testDataIndex + 1)
        val root = BazelTestUtil.getFileFromBazelRuntime("@community//plugins/kotlin:${testDataRoot.invariantSeparatorsPathString}")
        return root.resolve(testDataRoot.relativize(normalizedPathToResolve)).toFile()
    }
}
