// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.openapi.application.runReadAction
import com.sun.jdi.ThreadReference
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.FileRankingCalculator
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.junit.Assert
import java.io.File

// We can't determine which .kt file transformed into which .class files,
// so we have to manually specify it in test files.
private const val PRODUCED_CLASS_NAMES_DIRECTIVE = "// PRODUCED_CLASS_NAMES:"
// For anonymous classes, which may not exist if INDY lambdas used
private const val PRODUCED_CLASS_NAME_OPTIONAL_SUFFIX = "(optional)"

abstract class AbstractFileRankingTest : LowLevelDebuggerTestBase() {
    override fun doTest(
        options: Set<String>,
        mainThread: ThreadReference,
        sourceFiles: List<KtFile>,
        bindingContext: BindingContext,
        jvmSrcDir: File,
        outputFiles: List<CompiledClassFile>,
    ) {
        val doNotCheckClassFqName = "DO_NOT_CHECK_CLASS_FQNAME" in options
        val strictMode = "DISABLE_STRICT_MODE" !in options

        val classNameToKtFile = collectClassNamesToKtFiles(sourceFiles, outputFiles)
        val files = classNameToKtFile.values.distinct()
        val expectedRanks: Map<Pair<KtFile, Int>, Int> = files.asSequence().flatMap { ktFile ->
            ktFile.text.lines()
                .asSequence()
                .withIndex()
                .map {
                    val matchResult = "^.*// (R: (-?\\d+)( L: (\\d+))?)\\s*$".toRegex().matchEntire(it.value) ?: return@map null

                    val rank = matchResult.groupValues[2].toInt()
                    val line = matchResult.groupValues.getOrNull(4)?.takeIf { !it.isEmpty() }?.toInt()

                    if (line != null && line != it.index + 1) {
                        throw IllegalArgumentException("Bad line in directive at ${ktFile.name}:${it.index + 1}\n${it.value}")
                    }

                    (ktFile to it.index + 1) to rank
                }
                .filterNotNull()
        }.toMap()

        val calculator = object : FileRankingCalculator(checkClassFqName = !doNotCheckClassFqName) {
            override fun analyze(element: KtElement) = bindingContext
        }

        val problems = mutableListOf<String>()

        val skipClasses = skipLoadingClasses(options)
        for (outputFile in outputFiles) {
            val className = outputFile.qualifiedName
            if (className in skipClasses) {
                continue
            }

            val expectedFile = classNameToKtFile[className] ?: throw IllegalStateException("Can't find source for $className")

            val jdiClass = mainThread.virtualMachine().classesByName(className).singleOrNull()
                ?: error("Class '$className' was not found in the debuggee process class loader")

            val locations = DebuggerUtilsAsync.allLineLocationsSync(jdiClass)
            assert(locations.isNotEmpty()) { "There are no locations for class $className" }

            val allFilesWithSameName = files.filter { it.name == expectedFile.name }
            for (location in locations) {
                if (location.method().isBridge || location.method().isSynthetic) continue

                val fileWithRankings: Map<KtFile, Int> = runReadAction {
                    calculator.rankFiles(allFilesWithSameName, location)
                }

                for ((ktFile, rank) in fileWithRankings) {
                    val expectedRank = expectedRanks[ktFile to (location.lineNumber())]
                    if (expectedRank != null) {
                        Assert.assertEquals("Invalid expected rank at $location", expectedRank, rank)
                    }
                }

                val fileWithMaxScore = fileWithRankings.maxByOrNull { it.value }!!
                val actualFile = fileWithMaxScore.key

                if (strictMode) {
                    require(fileWithMaxScore.value >= 0) { "Max score is negative at $location" }

                    // Allow only one element with max ranking
                    require(fileWithRankings.filter { it.value == fileWithMaxScore.value }.count() == 1) {
                        "Score is the same for several files at $location"
                    }
                }

                if (actualFile != expectedFile) {
                    problems += "Location ${location.sourceName()}:${location.lineNumber() - 1} is associated with a wrong KtFile:\n" +
                            "    - expected: ${expectedFile.virtualFilePath}\n" +
                            "    - actual: ${actualFile.virtualFilePath}"
                }
            }
        }

        if (problems.isNotEmpty()) {
            throw AssertionError(buildString {
                appendLine("There were association errors:").appendLine()
                problems.joinTo(this, "\n\n")
            })
        }
    }

    override fun skipLoadingClasses(options: Set<String>): Set<String> {
        val skipClasses = options.mapTo(mutableSetOf()) { it.substringAfter("DO_NOT_LOAD:", "").trim() }
        skipClasses.remove("")
        return skipClasses
    }
}

private fun collectClassNamesToKtFiles(
    sourceFiles: List<KtFile>,
    outputFiles: List<LowLevelDebuggerTestBase.CompiledClassFile>,
): Map<String, KtFile> =
    runReadAction {
        buildMap {
            for (sourceFile in sourceFiles) {
                val classNames = InTextDirectivesUtils.findListWithPrefixes(sourceFile.text, PRODUCED_CLASS_NAMES_DIRECTIVE)
                if (classNames.isEmpty()) {
                    error("Expected at least one $PRODUCED_CLASS_NAMES_DIRECTIVE directive in file ${sourceFile.name}")
                }
                for (classNameWithSuffix in classNames) {
                    val isOptional = classNameWithSuffix.endsWith(PRODUCED_CLASS_NAME_OPTIONAL_SUFFIX)
                    val className = if (isOptional) {
                        classNameWithSuffix.substringBefore(PRODUCED_CLASS_NAME_OPTIONAL_SUFFIX)
                    } else {
                        classNameWithSuffix
                    }
                    assert(isOptional || outputFiles.any { it.qualifiedName == className}) { "Class name $className not found in output files"}
                    val file = get(className)
                    if (file != null) {
                        error("Same class name \"$className\" specified twice: in ${file.name} and ${sourceFile.name}")
                    }
                    put(className, sourceFile)
                }
            }
        }
    }

abstract class AbstractK1IdeK2CodeFileRankingTest : AbstractFileRankingTest() {
    override val compileWithK2 = true

    override val lambdasGenerationScheme = JvmClosureGenerationScheme.INDY
}
