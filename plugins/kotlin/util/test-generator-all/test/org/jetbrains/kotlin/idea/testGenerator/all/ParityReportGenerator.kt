// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testGenerator.all

import org.jetbrains.kotlin.fe10.testGenerator.assembleK1Workspace
import org.jetbrains.kotlin.fir.testGenerator.assembleK2Workspace
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.testGenerator.generator.SuiteElement
import org.jetbrains.kotlin.testGenerator.generator.methods.TestCaseMethod
import org.jetbrains.kotlin.testGenerator.generator.toRelativeStringSystemIndependent
import org.jetbrains.kotlin.testGenerator.model.TWorkspace
import java.io.File
import java.util.Date
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
    private const val SUCCESS_RATE_THRESHOLD = 85

    val k1Variation = Variation("K1", setOf("// IGNORE_K1", "/* IGNORE_K1", "\"enabledInK1\": \"false\""))
    val k2Variation = Variation("K2", setOf("// IGNORE_K2", "/* IGNORE_K2", "\"enabledInK2\": \"false\""))

    fun generateReport(k1Workspace: TWorkspace, k2Workspace: TWorkspace) {
        val md = buildString {
            appendLine("# K2/K1 feature parity report").appendLine().appendLine()
            appendLine("Generated on ${Date()}").appendLine()

            val extraBuilder = StringBuilder()
            val k1Cases = buildCases("K1", k1Workspace, extraBuilder)
            val k2Cases = buildCases("K2", k2Workspace, extraBuilder)

            val k1InvertedFileCases = toInvertedFileCases(k1Cases)
            val k2InvertedFileCases = toInvertedFileCases(k2Cases)

            reportK1OnlyFilesDetails(k1InvertedFileCases, k2InvertedFileCases, extraBuilder)
            reportSharedFilesDetails(k1InvertedFileCases, k2InvertedFileCases, this)

            append(extraBuilder)
        }
        File(KotlinRoot.DIR, "k2-k1-parity-report.md").writeText(md.replace("\n", System.lineSeparator()))
    }

    data class CaseNameFile(val generatedClassName: String, val testCaseMethod: TestCaseMethod, val fileName: String)
    data class Case(val clusterName: String, val files: List<CaseNameFile>)

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

                    val testCaseMethods = suiteElements.fold(mutableMapOf<String, List<TestCaseMethod>>()) { acc, curr ->
                        curr.testCaseMethods().forEach { (k, v) ->
                            acc[k] = (acc[k] ?: ArrayList<TestCaseMethod>()) + v
                        }
                        acc
                    }
                    if (testCaseMethods.any { it.value.any { !it.file.exists() } }) {
                        error(testCaseMethods.flatMap { it.value.filter { !it.file.exists() } }.joinToString())
                    }
                    testCaseMethods.forEach { (className, files) ->
                        val testDataMethodFiles = files.filter { it.file.isFile }
                        val fs = if (testDataMethodFiles.isEmpty()) {
                            files.mapNotNull {
                                val f = File(it.file, it.file.name + ".kt")
                                if (!f.isFile) return@mapNotNull null
                                it.copy(file = f)
                            }
                        } else {
                            testDataMethodFiles
                        }

                        if (fs.isEmpty()) {
                            if (files.any { it.file.isDirectory }) {
                                val directories = files
                                    .filter { it.file.isDirectory }
                                    .map { it.file.toRelativeKotlinRoot() }
                                stringBuilder.appendLine().appendLine("$className has directories")
                                for (s in directories) {
                                    stringBuilder.append(" * ").appendLine(s)
                                }
                            }
                        } else {
                            this.add(Case(clusterName, fs.map { CaseNameFile(className, it, it.file.toRelativeKotlinRoot()) }))
                        }
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
                    put(file.fileName, case)
                }
            }
        }

    private fun reportSharedFilesDetails(invertedK1FileCases: Map<String, Case>, invertedK2FileCases: Map<String, Case>, stringBuilder: StringBuilder) {
        val sharedFiles = buildList<Pair<String, Case>> {
            for (invertedK2FileCasesEntry in invertedK2FileCases) {
                val fileName: String = invertedK2FileCasesEntry.key
                if (fileName in invertedK1FileCases) {
                    add(fileName to invertedK2FileCasesEntry.value)
                }
            }
        }

        val testClassesPerClassName = mutableMapOf<String, AtomicInteger>()
        val extensions = mutableSetOf<String>()
        val ignored = BooleanArray(2)
        for (fileNameToCase in sharedFiles) {
            val fileName = fileNameToCase.first
            val case: Case = fileNameToCase.second
            val file = File(KotlinRoot.DIR, fileName)
            extensions += file.name.substringAfter('.')

            val caseNameFile: CaseNameFile = case.files.firstOrNull() ?: continue

            testClassesPerClassName.computeIfAbsent(caseNameFile.generatedClassName){ AtomicInteger() }.incrementAndGet()

            ignored.fill(false)
            if (caseNameFile.testCaseMethod.ignored) {
                ignored[1] = true
            }
            k1Variation.successTestClasses.computeIfAbsent(caseNameFile.generatedClassName) { AtomicInteger() }
            k2Variation.successTestClasses.computeIfAbsent(caseNameFile.generatedClassName) { AtomicInteger() }
            file.handleIgnored(k1Variation, k2Variation, ignored = ignored) {
                it.successTestClasses[caseNameFile.generatedClassName]!!.incrementAndGet()
            }
        }

        with(stringBuilder) {
            appendLine("## Shared cases")
            appendLine("shared ${sharedFiles.size} files out of ${k2Variation.successTestClasses.size} cases").appendLine()

            appendLine("| Status | Case name | Success rate, % | K2 files | K1 files | Total files |")
            appendLine("| -- | -- | --  | -- | -- | -- |")

            k2Variation.successTestClasses.map { (name, successK2Atomic) ->
                val successK2 = successK2Atomic.get()
                val successK1 = k1Variation.successTestClasses[name]?.get() ?: 0
                val files = testClassesPerClassName[name]!!.get()
                StatusLine(name, successK2, successK1, files)
            }.groupBy {
                // group all sub-elements
                val firstPart = it.name.substringBefore("$")
                val substringAfter = it.name.substringAfter("$")
                val lastPart = substringAfter.substringBefore("$")
                "$firstPart$$lastPart"
            }.map {
                StatusLine(it.key, it.value.sumOf { it.k2 }, it.value.sumOf { it.k1 }, it.value.sumOf { it.files })
            }.groupBy {
                it.name.substringBefore("$")
            }.map {
                StatusLine(it.key, it.value.map { it.k2 }.min(), it.value.map { it.k1 }.min(), it.value.sumOf { it.files }) to it.value
            }
                .sortedWith(compareBy({ it.first.rate }, { it.first.name }))
                .forEach {
                    val first = it.first
                    val second = it.second
                    // no reasons to report all sub-elements if total it is 100%
                    if (first.rate == 100) {
                        val line = StatusLine("[${first.name}]", second.sumOf { it.k2 }, second.sumOf { it.k1 }, second.sumOf { it.files })
                        appendLine(line.renderToMd())
                    } else {
                        // summary
                        if (first.name != second.first().name && second.size > 1) {
                            val line = StatusLine("[${first.name}]", second.sumOf { it.k2 }, second.sumOf { it.k1 }, second.sumOf { it.files })
                            appendLine(line.renderToMd())
                        }
                        // detailed
                        second.sortedWith(compareBy({ it.rate }, { it.name })).forEach {
                            appendLine(it.renderToMd())
                        }
                    }
                }

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
        val rate: Int = if (k1 > 0) Math.round(100.0 * k2 / k1).toInt() else 100
        val passed = rate >= SUCCESS_RATE_THRESHOLD

        fun renderToMd(): String {
          val shortName = if (!name[0].isLetter()) (name[0] + name.substringAfterLast('.')) else name.substringAfterLast('.')
          return PIPE + listOf(if (passed) ":white_check_mark:" else ":x:", shortName, rate, k2, k1, files).joinToString(
            PIPE) + PIPE
        }
    }

    private fun File.handleIgnored(vararg variations: Variation, ignored: BooleanArray, action:(Variation) -> Unit) {
        val readText = readText()
        for ((idx, variation) in variations.withIndex()) {
            if (!ignored[idx] && variation.ignoreDirectives.any { readText.contains(it) }) {
                ignored[idx] = true
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
            k1OnlyCases.flatMap { it.files }.map { " * " + it.generatedClassName }.toSortedSet().forEach { appendLine(it) }
            appendLine("---")
        }
    }
}