// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.testGenerator.generator.methods

import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.testGenerator.generator.Code
import org.jetbrains.kotlin.testGenerator.generator.TestMethod
import org.jetbrains.kotlin.testGenerator.generator.appendAnnotation
import org.jetbrains.kotlin.testGenerator.generator.appendBlock
import org.jetbrains.kotlin.testGenerator.model.TAnnotation
import org.jetbrains.kotlin.testGenerator.model.makeJavaIdentifier
import java.io.File

data class TestCaseMethod(
    private val methodNameBase: String,
    private val contentRootPath: String,
    private val localPath: String,
    private val isCompilerTestData: Boolean,
    private val passTestDataPath: Boolean,
    val file: File,
    val ignored: Boolean,
    private val annotations: List<TAnnotation> = emptyList()
) : TestMethod {
    override val methodName = run {
        "test" + when (val qualifier = File(localPath).parentFile?.path?.replace(File.separatorChar, '/') ?: "") {
            "" -> methodNameBase
            else -> makeJavaIdentifier(qualifier).capitalize() + "_" + methodNameBase
        }
    }

    fun embed(path: String): TestCaseMethod {
        val f = File(path, localPath)
        return TestCaseMethod(
            methodNameBase,
            contentRootPath,
            f.path.replace(File.separatorChar, '/'),
            isCompilerTestData,
            passTestDataPath,
            f,
            ignored,
            annotations
        )
    }

    fun testDataPath(parent: File): File =
        File(parent, localPath)

    override fun Code.render() {
        if (ignored) return
        appendAnnotation(TAnnotation<TestMetadata>(localPath))
        annotations.forEach { appendAnnotation(it) }

        appendBlock("public void $methodName() throws Exception") {
            if (!passTestDataPath) {
                append("performTest();")
            } else if (isCompilerTestData) {
                val path = contentRootPath.substringAfter(TestKotlinArtifacts.compilerTestDataDir.name + "/")
                append("runTest(${TestKotlinArtifacts::compilerTestData.name}(\"$path\"));")
            } else {
                append("runTest(\"$contentRootPath\");")
            }
        }
    }
}