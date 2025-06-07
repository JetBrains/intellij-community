// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.testGenerator.generator

import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.TestIndexingModeSupporter
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import junit.framework.ComparisonFailure
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.test.TestIndexingMode
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.testGenerator.model.TAnnotation
import org.jetbrains.kotlin.testGenerator.model.TGroup
import org.jetbrains.kotlin.testGenerator.model.TSuite
import org.jetbrains.kotlin.testGenerator.model.TWorkspace
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.*

object TestGenerator {
    fun write(workspace: TWorkspace, isUpToDateCheck: Boolean = false) {
        for (group in workspace.groups) {
            for (suite in group.suites) {
                for (platform in suite.platforms) {
                    write(suite, group, isUpToDateCheck, platform = platform)
                }
            }
        }
    }

    private fun write(suite: TSuite, group: TGroup, isUpToDateCheck: Boolean, platform: KMPTestPlatform = KMPTestPlatform.Unspecified) {
        val packageName = suite.generatedClassPackage
        val rootModelName = suite.generatedClassShortName(platform)

        val content = buildCode {
            appendCopyrightComment()
            newLine()

            appendLine("package $packageName;")
            newLine()

            appendImports(getImports(suite, group, platform))
            appendGeneratedComment()
            appendAnnotation(TAnnotation<SuppressWarnings>("all"))
            appendAnnotation(TAnnotation<TestRoot>(group.modulePath))
            appendAnnotation(TAnnotation<TestDataPath>("\$CONTENT_ROOT"))

            val singleModel = suite.models.singleOrNull()
            if (singleModel != null) {
                append(SuiteElement.create(group, suite, singleModel, rootModelName, platform, isNested = false))
            } else {
                val runWithClass = suite.models.map { it.runWithClass }.distinct().single()
                appendAnnotation(TAnnotation<RunWith>(runWithClass))
                appendBlock("public abstract class $rootModelName extends ${suite.abstractTestClass.simpleName}") {
                    val children = suite.models
                        .map { SuiteElement.create(group, suite, it, it.testClassName, platform, isNested = true) }
                    appendList(children, separator = "\n\n")
                }
            }
            newLine()
        }

        val filePath = suite.generatedClassFqName(platform).replace('.', '/') + ".java"
        val file = File(group.testSourcesRoot, filePath)
        write(file, postProcessContent(content), isUpToDateCheck)
    }
}

internal fun getImports(suite: TSuite, group: TGroup, platform: KMPTestPlatform): Collection<String> {
    val imports = mutableSetOf<String>()

    imports += TestDataPath::class.java.canonicalName
    imports += KotlinPluginMode::class.java.canonicalName
    imports += TestRoot::class.java.canonicalName

    suite.models.forEach { imports += it.runWithClass.canonicalName }

    if (suite.models.any { it.passTestDataPath }) {
        imports += KotlinTestUtils::class.java.canonicalName
    }

    if (suite.indexingMode.isNotEmpty()) {
        imports += TestIndexingModeSupporter::class.java.canonicalName
        imports += TestIndexingMode::class.java.canonicalName
        suite.indexingMode.map {
            imports += "static ${IndexingMode::class.java.canonicalName}.${it.name}"
        }
    }

    imports += TestMetadata::class.java.canonicalName
    imports += RunWith::class.java.canonicalName

    if (platform.isSpecified) {
        imports += KMPTestPlatform::class.java.canonicalName
    }

    imports.addAll(suite.imports)

    if (suite.models.any { it.targetBackend != TargetBackend.ANY }) {
        imports += TargetBackend::class.java.canonicalName
    }

    val superPackageName = suite.abstractTestClass.`package`.name
    val selfPackageName = suite.generatedClassPackage
    if (superPackageName != selfPackageName) {
        imports += suite.abstractTestClass.kotlin.java.canonicalName
    }

    if (group.isCompilerTestData) {
        imports += "static ${TestKotlinArtifacts::class.java.canonicalName}.${TestKotlinArtifacts::compilerTestData.name}"
    }

    return imports
}

internal fun postProcessContent(text: String): String {
    return text.lineSequence()
        .map { it.trimEnd() }
        .joinToString(System.getProperty("line.separator"))
}

internal fun Code.appendImports(imports: Collection<String>) {
    if (imports.isNotEmpty()) {
        imports.forEach { appendLine("import $it;") }
        newLine()
    }
}

internal fun Code.appendCopyrightComment() {
    val year = GregorianCalendar()[Calendar.YEAR]
    appendLine("// Copyright 2000-$year JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.")
}

internal fun Code.appendGeneratedComment() {
    appendDocComment(
        """
            This class is generated by {@link org.jetbrains.kotlin.testGenerator.generator.TestGenerator}.
            DO NOT MODIFY MANUALLY.
        """.trimIndent()
    )
}

internal fun write(file: File, content: String, isUpToDateCheck: Boolean) {
    val oldContent = file.takeIf { it.isFile }?.readText() ?: ""

    if (normalizeContent(content) != normalizeContent(oldContent)) {
        if (isUpToDateCheck) {
            if (file.exists()) {
                throw FileComparisonFailedError(
                    message = "'${file.name}' is not up to date\nUse 'Generate Kotlin Tests' run configuration to regenerate tests\n",
                    expected = oldContent,
                    actual = content,
                    expectedFilePath = file.absolutePath,
                    actualFilePath = file.absolutePath
                )
            } else {
                throw ComparisonFailure(
                    /* message = */ "'${file.name}' is not up to date\nUse 'Generate Kotlin Tests' run configuration to regenerate tests\n",
                    /* expected = */ oldContent,
                    /* actual = */ content
                )
            }
        }

        Files.createDirectories(file.toPath().parent)
        file.writeText(content)
        val path = file.toRelativeStringSystemIndependent(KotlinRoot.DIR)
        println("Updated $path")
    }
}

internal fun normalizeContent(content: String): String = content.replace(Regex("\\R"), "\n")
    .lineSequence().withIndex()
    // Keeping a copyright notice up to date is a good idea, but failing a test upon the first day of each year is not
    .filterNot { (index, line) -> index == 0 && line.startsWith("// Copyright") }
    .joinToString(separator = "\n")