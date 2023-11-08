// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testGenerator.all

import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.testGenerator.generator.toRelativeStringSystemIndependent
import org.jetbrains.kotlin.fe10.testGenerator.assembleK1Workspace
import org.jetbrains.kotlin.fir.testGenerator.assembleK2Workspace
import org.jetbrains.kotlin.testGenerator.generator.SuiteElement
import org.jetbrains.kotlin.testGenerator.model.TWorkspace
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    val k2Workspace = assembleK2Workspace()
    val k1Workspace = assembleK1Workspace()

    ParityReportGenerator.generateReport(k1Workspace = k1Workspace, k2Workspace = k2Workspace)
}

object ParityReportGenerator {

    data class Variation(
        val name: String,
        val ignoreDirectives: Set<String>,
        val successTestClasses: MutableMap<String, AtomicInteger> = mutableMapOf<String, AtomicInteger>()
    ) {
        fun totalCount(): Int = successTestClasses.map { it.value }.sumOf { it.get() }
    }

    private const val PIPE = " | "
    private const val successRateThreshold = 85

    val k1Variation = Variation("K1", setOf("// IGNORE_K1", "\"enabledInK1\": \"false\""))
    val k2Variation = Variation("K2", setOf("// IGNORE_K2", "\"enabledInK2\": \"false\""))

    fun generateReport(k1Workspace: TWorkspace, k2Workspace: TWorkspace) {
        val md = buildString {
            appendLine("# K2/K1 feature parity report").appendLine()
            val extraBuilder = StringBuilder()
            val k1Cases = buildCases("K1", k1Workspace, extraBuilder)
            val k2Cases = buildCases("K2", k2Workspace, extraBuilder)

            val k1InvertedFileCases = toInvertedFileCases(k1Cases)
            val k2InvertedFileCases = toInvertedFileCases(k2Cases)

            reportK1OnlyFilesDetails(k1InvertedFileCases, k2InvertedFileCases, extraBuilder)
            reportSharedFilesDetails(k1InvertedFileCases, k2InvertedFileCases, this)

            append(extraBuilder)
        }
        File("parity-report.md").writeText(md.replace("\n", System.lineSeparator()))
    }

    data class Case(
        val clusterName: String,
        val generatedClassName: String,
        val files: List<String>
    )

    private fun buildCases(clusterName: String, workspace: TWorkspace, stringBuilder: StringBuilder): List<Case> =
        buildList<Case> {
            stringBuilder.append("## Build cases for $clusterName\n")
            for (group in workspace.groups) {
                for (suite in group.suites) {
                    if (!suite.commonSuite) continue

                    val singleModel = suite.models.singleOrNull()
                    val suiteElements: List<SuiteElement> = buildList {
                        if (singleModel != null) {
                            val suiteElement = SuiteElement.create(
                                group,
                                suite,
                                singleModel,
                                suite.generatedClassName.substringAfterLast('.'),
                                isNested = false
                            )
                            add(suiteElement)
                        } else {
                            addAll(suite.models
                                .map { SuiteElement.create(group, suite, it, it.testClassName, isNested = true) })
                        }
                    }

                    val testDataMethodPaths = suiteElements.flatMap { it.testDataMethodPaths() }
                    val testDataMethodFiles = testDataMethodPaths.filter { it.isFile }
                    val files = if (testDataMethodFiles.isEmpty()) {
                        testDataMethodPaths.map { File(it, it.name + ".kt") }.filter { it.isFile }
                    } else {
                        testDataMethodFiles
                    }
                        .map { it.toRelativeKotlinRoot() }

                    if (files.isEmpty()) {
                        if (testDataMethodPaths.any { it.isDirectory }) {
                            val directories = testDataMethodPaths
                                .filter { it.isDirectory }
                                .map { it.toRelativeKotlinRoot() }
                            stringBuilder.appendLine().appendLine("${suite.generatedClassName} has directories")
                            for (s in directories) {
                                stringBuilder.append(" * ").appendLine(s)
                            }
                        } else {
                            error("${suite.generatedClassName} has no files: ${testDataMethodPaths.map { it.toRelativeKotlinRoot() }}")
                        }
                    } else {
                        this.add(Case(clusterName, suite.generatedClassName, files))
                    }
                }
            }
        }

    private fun File.toRelativeKotlinRoot() =
        toRelativeStringSystemIndependent(KotlinRoot.DIR)

    private fun toInvertedFileCases(cases: Collection<Case>): Map<String, Case> =
        buildMap {
            for (case in cases) {
                for (file in case.files) {
                    put(file, case)
                }
            }
        }

    private fun reportSharedFilesDetails(invertedK1FileCases: Map<String, Case>, invertedK2FileCases: Map<String, Case>, stringBuilder: StringBuilder) {
        val sharedFiles = buildList<Pair<String, Case>> {
            for (invertedK2FileCasesEntry in invertedK2FileCases) {
                val fileName = invertedK2FileCasesEntry.key
                if (fileName in invertedK1FileCases) {
                    add(fileName to invertedK2FileCasesEntry.value)
                }
            }
        }

        val testClassesPerClassName = mutableMapOf<String, AtomicInteger>()
        val extensions = mutableSetOf<String>()
        for (fileNameToCase in sharedFiles) {
            val fileName = fileNameToCase.first
            val case = fileNameToCase.second
            val file = File(KotlinRoot.DIR, fileName)
            extensions += file.name.substringAfter('.')

            testClassesPerClassName.computeIfAbsent(case.generatedClassName){ AtomicInteger() }.incrementAndGet()

            file.handleIgnored(k1Variation, k2Variation) {
                it.successTestClasses.computeIfAbsent(case.generatedClassName){ AtomicInteger() }.incrementAndGet()
            }
        }

        with(stringBuilder) {
            appendLine("## Shared cases")
            appendLine("shared ${sharedFiles.size} files out of ${k2Variation.successTestClasses.size} cases").appendLine()

            appendLine("| Status | Case name | Rate, % | K2 files | K1 files | Total files |")
            appendLine("| -- | -- | --  | -- | -- | -- |")

            k2Variation.successTestClasses.map { (name, successK2Atomic) ->
                val successK2 = successK2Atomic.get()
                val successK1 = k1Variation.successTestClasses[name]!!.get()
                val perCase = testClassesPerClassName[name]!!.get()
                StatusLine(name, successK2, successK1, perCase)
            }.sortedWith(compareBy({ it.rate }, { it.name }))
                .forEach { appendLine(it.renderToMd()) }

            appendLine()
            val successK1 = k1Variation.totalCount()
            val successK2 = k2Variation.totalCount()
            val totalPerCase = testClassesPerClassName.map { it.value }.sumOf { it.get() }
            appendLine("### Extensions").appendLine()
            appendLine(extensions.joinToString()).appendLine()
            appendLine("---")
            appendLine("## Total ")
            appendLine(" * K1: $successK1 rate: ${Math.round(100.0 * successK1 / sharedFiles.size)} % of $totalPerCase files")
            appendLine(" * K2: $successK2 rate: ${Math.round(100.0 * successK2 / sharedFiles.size)} % of $totalPerCase files")
            appendLine("---").appendLine()
        }
    }

    private data class StatusLine(
        val name: String,
        val k2: Int,
        val k1: Int,
        val files: Int
    ) {
        val rate = Math.round(100.0 * k2 / k1)
        val passed = rate >= successRateThreshold

        fun renderToMd(): String =
            PIPE + listOf(if (passed) ":white_check_mark:" else ":x:", name.substringAfterLast('.'), rate, k2, k1, files).joinToString(PIPE) + PIPE
    }

    private fun File.handleIgnored(vararg variations: Variation, action:(Variation) -> Unit) {
        val readText = readText()
        val ignored = BooleanArray(variations.size) { false }
        for ((idx, variation) in variations.withIndex()) {
            if (variation.ignoreDirectives.any { readText.contains(it) }) {
                ignored[idx] = true
            }
        }
        val directivesFile = File(parentFile, "directives.txt")
        if (directivesFile.exists()) {
            val directivesFileText = directivesFile.readText()
            for ((idx, variation) in variations.withIndex()) {
                if (ignored[idx]) continue

                if (variation.ignoreDirectives.any { directivesFileText.contains(it) }) {
                    ignored[idx] = true
                }
            }
        }

        for ((idx, variation) in variations.withIndex()) {
            if (!ignored[idx]) {
                action(variation)
            }
        }
    }

    private fun reportK1OnlyFilesDetails(invertedK1FileCases: Map<String, Case>, invertedK2FileCases: Map<String, Case>, stringBuilder: StringBuilder) {
        val k1OnlyCases = mutableSetOf<Case>()
        val k1OnlyFiles = buildSet<String> {
            for (invertedK1FileCasesEntry in invertedK1FileCases) {
                val fileName = invertedK1FileCasesEntry.key
                val case = invertedK1FileCasesEntry.value
                if (fileName !in invertedK2FileCases) {
                    add(fileName)
                    k1OnlyCases.add(case)
                }
            }
        }

        with(stringBuilder) {
            appendLine("## K1 only cases").appendLine()
            appendLine("${k1OnlyCases.size} K1 only cases (${k1OnlyFiles.size} files):").appendLine()
            appendLine(k1OnlyCases.map { " * " + it.generatedClassName }.joinToString(separator = "\n"))
            appendLine("---")
        }
    }
}