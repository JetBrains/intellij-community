// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.jetbrains.kotlin.idea.debugger.core.KotlinExceptionFilterFactory
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

private data class SuffixOption(val suffix: String, val expectedLine: Int, val expectedColumn: Int)

@RunWith(JUnit38ClassRunner::class)
class KotlinExceptionFilterTest : KotlinLightCodeInsightFixtureTestCase() {
    private val suffixPlug = "<suffix>"
    private val pathPlug = "<path>"

    private val templates = listOf(
        "\tat 1   main.kexe\t\t 0x000000010d7cdb4c kfun:package.function(kotlin.Int) + 108 ($pathPlug:$suffixPlug)\n"
    )

    private val suffixOptions = listOf(
        SuffixOption("10:1", 9, 1),
        SuffixOption("14:11", 13, 11),
        SuffixOption("<unknown>", 0, 0)
    )

    override fun getProjectDescriptor(): LightProjectDescriptor = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR

    fun testDifferentLocations() {
        for (template in templates) {
            for (suffixOption in suffixOptions) {
                val templateWithSuffix = template.replace(suffixPlug, suffixOption.suffix)
                doTest(templateWithSuffix, suffixOption.expectedLine, suffixOption.expectedColumn)
            }
        }
    }

    fun doTest(template: String, expectedLine: Int, expectedColumn: Int) {
        val filter = KotlinExceptionFilterFactory().create(GlobalSearchScope.allScope(project))

        val rootDir = IDEA_TEST_DATA_DIR.resolve("debugger/nativeExceptions")

        for (ioFile in rootDir.listFiles()!!) {
            val absolutePath = ioFile.absolutePath
            val virtualFile = ioFile.toVirtualFile() ?: continue

            val exceptionLine = template.replace(pathPlug, absolutePath)

            fun errorMessage(detail: String) = "Failed to parse Kotlin Native exception '$exceptionLine': $detail"

            val filterResult = filter.applyFilter(exceptionLine, exceptionLine.length)
            assertNotNull(errorMessage("filename is not found by parser"), filterResult)
            val fileHyperlinkInfo = filterResult?.firstHyperlinkInfo as FileHyperlinkInfo

            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            assertNotNull(errorMessage("test file $absolutePath could not be found in repository"), document)
            val expectedOffset = document!!.getLineStartOffset(expectedLine) + expectedColumn

            val descriptor = fileHyperlinkInfo.descriptor
            assertNotNull(errorMessage("found file hyperlink with null descriptor"), descriptor)
            assertEquals(errorMessage("different filename parsed"), virtualFile.canonicalPath, descriptor?.file?.canonicalPath)
            assertEquals(errorMessage("different offset parsed"), expectedOffset, descriptor?.offset)
        }
    }
}