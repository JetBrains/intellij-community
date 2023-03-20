// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.base.psi.getLineEndOffset
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.BreakpointChecker
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile

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
    }

    private fun checkBreakpoints(file: KtFile, checker: BreakpointChecker): String {
        val lineCount = file.getLineCount()
        return (0 until lineCount).joinToString("\n") { line -> checkLine(file, line, checker) }
    }

    private fun checkLine(file: KtFile, line: Int, checker: BreakpointChecker): String {
        val lineText = file.getLine(line)
        val expectedBreakpointTypes = lineText.substringAfterLast(COMMENT).trim().split(",").map { it.trim() }.toSortedSet()
        val actualBreakpointTypes = checker.check(file, line).map { it.prefix }.distinct().toSortedSet()

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

    private fun PsiFile.getLine(line: Int): String {
        val start = getLineStartOffset(line, skipWhitespace = false) ?: error("Cannot find start for line $line")
        val end = getLineEndOffset(line) ?: error("Cannot find end for line $line")
        if (start >= end) {
            return ""
        }

        return text.substring(start, end)
    }
}
