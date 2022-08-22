// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.testGenerator.generator

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.test.*
import org.jetbrains.kotlin.testGenerator.model.*
import org.junit.runner.RunWith
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.base.test.TestRoot
import java.io.File
import java.nio.file.Files
import java.util.*

object TestGenerator {
    fun write(workspace: TWorkspace, isUpToDateCheck: Boolean = false) {
        for (group in workspace.groups) {
            for (suite in group.suites) {
                write(suite, group, isUpToDateCheck)
            }
        }
    }

    private fun write(suite: TSuite, group: TGroup, isUpToDateCheck: Boolean) {
        val packageName = suite.generatedClassName.substringBeforeLast('.')
        val rootModelName = suite.generatedClassName.substringAfterLast('.')

        val content = buildCode {
            appendCopyrightComment()
            newLine()

            appendLine("package $packageName;")
            newLine()

            appendImports(getImports(suite, group))
            appendGeneratedComment()
            appendAnnotation(TAnnotation<SuppressWarnings>("all"))
            appendAnnotation(TAnnotation<TestRoot>(group.modulePath))
            appendAnnotation(TAnnotation<TestDataPath>("\$CONTENT_ROOT"))

            val singleModel = suite.models.singleOrNull()
            if (singleModel != null) {
                append(SuiteElement.create(group, suite, singleModel, rootModelName, isNested = false))
            } else {
                appendAnnotation(TAnnotation<RunWith>(JUnit3RunnerWithInners::class.java))
                appendBlock("public abstract class $rootModelName extends ${suite.abstractTestClass.simpleName}") {
                    val children = suite.models
                        .map { SuiteElement.create(group, suite, it, it.testClassName, isNested = true) }
                    appendList(children, separator = "\n\n")
                }
            }
            newLine()
        }

        val filePath = suite.generatedClassName.replace('.', '/') + ".java"
        val file = File(group.testSourcesRoot, filePath)
        write(file, postProcessContent(content), isUpToDateCheck)
    }

    private fun write(file: File, content: String, isUpToDateCheck: Boolean) {
        val oldContent = file.takeIf { it.isFile }?.readText() ?: ""

        if (normalizeContent(content) != normalizeContent(oldContent)) {
            if (isUpToDateCheck) error("'${file.name}' is not up to date\nUse 'Generate Kotlin Tests' run configuration")
            Files.createDirectories(file.toPath().parent)
            file.writeText(content)
            val path = file.toRelativeStringSystemIndependent(KotlinRoot.DIR)
            println("Updated $path")
        }
    }

    private fun normalizeContent(content: String): String = content.replace(Regex("\\R"), "\n")

    private fun getImports(suite: TSuite, group: TGroup): List<String> {
        val imports = mutableListOf<String>()

        imports += TestDataPath::class.java.canonicalName
        imports += JUnit3RunnerWithInners::class.java.canonicalName

        if (suite.models.any { it.passTestDataPath }) {
            imports += KotlinTestUtils::class.java.canonicalName
        }

        imports += TestMetadata::class.java.canonicalName
        imports += TestRoot::class.java.canonicalName
        imports += RunWith::class.java.canonicalName

        imports.addAll(suite.imports)

        if (suite.models.any { it.targetBackend != TargetBackend.ANY }) {
            imports += TargetBackend::class.java.canonicalName
        }

        val superPackageName = suite.abstractTestClass.`package`.name
        val selfPackageName = suite.generatedClassName.substringBeforeLast('.')
        if (superPackageName != selfPackageName) {
            imports += suite.abstractTestClass.kotlin.java.canonicalName
        }

        if (group.isCompilerTestData) {
            imports += "static ${TestKotlinArtifacts::class.java.canonicalName}.${TestKotlinArtifacts::compilerTestData.name}"
        }

        return imports
    }

    private fun postProcessContent(text: String): String {
        return text.lineSequence()
            .map { it.trimEnd() }
            .joinToString(System.getProperty("line.separator"))
    }

    private fun Code.appendImports(imports: List<String>) {
        if (imports.isNotEmpty()) {
            imports.forEach { appendLine("import $it;") }
            newLine()
        }
    }

    private fun Code.appendCopyrightComment() {
        val year = GregorianCalendar()[Calendar.YEAR]
        appendLine("// Copyright 2000-$year JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.")
    }

    private fun Code.appendGeneratedComment() {
        appendDocComment("""
            This class is generated by {@link org.jetbrains.kotlin.testGenerator.generator.TestGenerator}.
            DO NOT MODIFY MANUALLY.
        """.trimIndent())
    }
}