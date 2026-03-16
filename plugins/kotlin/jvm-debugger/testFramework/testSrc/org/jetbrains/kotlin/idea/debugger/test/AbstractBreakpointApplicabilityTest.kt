// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.base.psi.getLineEndOffset
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.debugger.breakpoints.KotlinLineBreakpointType
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.BreakpointChecker
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.BreakpointChecker.Companion.BREAKPOINT_TYPES
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractBreakpointApplicabilityTest : KotlinLightCodeInsightFixtureTestCase() {
    private companion object {
        private const val COMMENT = "///"
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    protected open fun doTest(unused: String) {
        val ktFile = myFixture.configureByFile(fileName()) as KtFile

        val actualContents = checkBreakpoints(ktFile, BreakpointChecker())
        KotlinTestUtils.assertEqualsToFile(dataFile(), actualContents)

        val outputFile = File(testDataDirectory, getTestName(true) + ".out")

        val actualHighlighting = checkHighlighting(ktFile, BreakpointChecker())
        KotlinTestUtils.assertEqualsToFile(outputFile, actualHighlighting)
    }

    private fun checkBreakpoints(file: KtFile, checker: BreakpointChecker): String =
        checkFile(file) { line -> checkLine(file, line, checker) }

    private fun checkFile(file: KtFile, checkLine: (Int) -> String?): String {
        val lineCount = file.getLineCount()
        return (0 until lineCount).mapNotNull(checkLine).joinToString("\n")
    }


    private fun checkLine(file: KtFile, line: Int, checker: BreakpointChecker): String {
        val lineText = file.getLine(line)
        val expectedBreakpointTypes = lineText.substringAfterLast(COMMENT).trim().split(",").map { it.trim() }.sorted()
        val actualBreakpointTypes = checker.check(file, line).map { it.prefix }.sorted()

        return if (expectedBreakpointTypes != actualBreakpointTypes) {
            val lineWithoutComments = lineText.substringBeforeLast(COMMENT).trimEnd()
            if (actualBreakpointTypes.isNotEmpty()) {
                "$lineWithoutComments $COMMENT " + actualBreakpointTypes.joinToString()
            } else {
                lineWithoutComments
            }
        } else {
            lineText
        }
    }

    private fun checkHighlighting(file: KtFile, checker: BreakpointChecker): String =
        checkFile(file) { line ->
            val breakpointTypes = checker.check(file, line)
            if (BreakpointChecker.BreakpointType.Line !in breakpointTypes &&
                BreakpointChecker.BreakpointType.Lambda !in breakpointTypes
            ) return@checkFile null
            val sourcePosition = org.jetbrains.debugger.SourceInfo(file.virtualFile, line)
            val breakpoints = KotlinLineBreakpointType().computeVariants(file.project, sourcePosition)

            if (breakpoints.isEmpty()) return@checkFile null
            val breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager()
            breakpoints.mapNotNull { variant ->
                val type = BREAKPOINT_TYPES[variant::class.java]!!
                val breakpoint = XDebuggerUtilImpl.addLineBreakpoint(breakpointManager, variant, file.virtualFile, line)
                val highlightingRange = variant.type.getHighlightRange(breakpoint)
                val previewHighlightingRange = variant.highlightRange
                if ((type == BreakpointChecker.BreakpointType.Line || type == BreakpointChecker.BreakpointType.All)
                    && highlightingRange == null && previewHighlightingRange == null
                ) {
                    // This is uninteresting case when whole line is highlighted
                    return@mapNotNull null
                }
                val highlightingText = getHighlightingText(highlightingRange, file)
                val preview = "\n  Preview: ${getHighlightingText(previewHighlightingRange, file)}"
                    .takeIf { highlightingRange != previewHighlightingRange }.orEmpty()
                "${type.prefix} ${line + 1} $highlightingText$preview"
            }.takeIf { it.isNotEmpty() }?.joinToString("\n")
        }

    private fun getHighlightingText(range: TextRange?, file: KtFile): String {
        if (range == null) return "Highlight whole line"
        val text = file.text.substring(range.startOffset, range.endOffset)
            .replace("\n", "\\n")
        return "Highlight range: '$text'"
    }

    private fun PsiFile.getLine(line: Int): String {
        val start = getLineStartOffset(line, skipWhitespace = false) ?: error("Cannot find start for line $line")
        val end = getLineEndOffset(line) ?: error("Cannot find end for line $line")
        if (start >= end) {
            return ""
        }

        return text.substring(start, end)
    }
}
