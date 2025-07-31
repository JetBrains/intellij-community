// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.testGenerator.generator

import com.intellij.openapi.application.PathManager
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
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinMavenUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.testGenerator.model.TAnnotation
import org.jetbrains.kotlin.testGenerator.model.TGroup
import org.jetbrains.kotlin.testGenerator.model.TSuite
import org.jetbrains.kotlin.testGenerator.model.TWorkspace
import org.jetbrains.tools.model.updater.KotlinTestsDependenciesUtil
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.*

object TestGenerator {
    fun writeLibrariesVersion(isUpToDateCheck: Boolean = false) {
        val kotlincKotlinCompilerCliVersion = KotlinMavenUtils.findLibraryVersion("kotlinc_kotlin_compiler_cli.xml")
        val kotlincKotlinJpsPluginTestsVersion = KotlinMavenUtils.findLibraryVersion("kotlinc_kotlin_jps_plugin_tests.xml")

        val kotlinCompilerCliVersionRegex = Regex("""kotlinCompilerCliVersion\s*=\s*"(\S+)"""")
        val kotlincKotlinJpsPluginTestsVersionRegex = Regex("""kotlincKotlinJpsPluginTestsVersion\s*=\s*"(\S+)"""")

        val kotlinDependenciesBazelFile = File(PathManager.getCommunityHomePath()).resolve("plugins/kotlin/kotlin_test_dependencies.bzl")
        val content = kotlinDependenciesBazelFile.readText()
        if (isUpToDateCheck) {
            kotlinCompilerCliVersionRegex.find(content)?.let { match ->
                if (match.groupValues[1] != kotlincKotlinCompilerCliVersion) {
                    error(
                        "Inconsistent version of kotlinc compiler cli version in '${kotlinDependenciesBazelFile.absolutePath}' " +
                                "expected $kotlincKotlinCompilerCliVersion actual ${match.groupValues[1]}"
                    )
                }
            } ?: error("Cannot find kotlinc compiler version in '${kotlinDependenciesBazelFile.absolutePath}'")
            kotlincKotlinJpsPluginTestsVersionRegex.find(content)?.let { match ->
                if (match.groupValues[1] != kotlincKotlinJpsPluginTestsVersion) {
                    error(
                        "Inconsistent version of JPS plugin tests version in '${kotlinDependenciesBazelFile.absolutePath}' " +
                                "expected $kotlincKotlinJpsPluginTestsVersion actual ${match.groupValues[1]}"
                    )

                }
            } ?: error("Cannot find JPS plugin tests version in '${kotlinDependenciesBazelFile.absolutePath}'")
            KotlinTestsDependenciesUtil.updateChecksum(true)
        } else {
            kotlinDependenciesBazelFile.writeText(
                content
                    .replace(
                        """kotlinCompilerCliVersion\s*=\s*"(\S+)"""".toRegex(),
                        "kotlinCompilerCliVersion = \"$kotlincKotlinCompilerCliVersion\""
                    )
                    .replace(
                        """kotlincKotlinJpsPluginTestsVersion\s*=\s*"\S+"""".toRegex(),
                        "kotlincKotlinJpsPluginTestsVersion = \"$kotlincKotlinJpsPluginTestsVersion\""
                    )
            )
            KotlinTestsDependenciesUtil.updateChecksum(false)
        }
    }

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
            appendAnnotation(TAnnotation<TestDataPath>($$"$CONTENT_ROOT"))

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
        .joinToString(System.lineSeparator())
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
            val tmpFile = Files.createTempFile(file.name, null).toFile()
            tmpFile.writeText(content)
            if (file.exists()) {
                throw FileComparisonFailedError(
                    message = "'${file.name}' is not up to date\nUse 'Generate Kotlin Tests' run configuration to regenerate tests\n",
                    expected = oldContent,
                    actual = content,
                    expectedFilePath = file.absolutePath,
                    actualFilePath = tmpFile.absolutePath
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