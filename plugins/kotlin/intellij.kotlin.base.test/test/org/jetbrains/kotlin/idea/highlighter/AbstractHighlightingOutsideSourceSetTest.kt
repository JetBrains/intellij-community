// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.utils.io.deleteRecursively
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

abstract class AbstractHighlightingOutsideSourceSetTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testFileOutsideSourceSet() {
        withTempDirectory { tempDirPath ->
            // no `<error>`/`<warning>` outside of source root
            val tempFilePath = tempDirPath.resolve("outsideSourceSet.kt").apply {
                writeText(
                    """class <symbolName>Foo</symbolName> {
                        val <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">field</symbolName>: UnresolvedClass = createUnresolved()

                        fun <symbolName>foo</symbolName>() {
                            val <symbolName>x</symbolName> = <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">field</symbolName>
                        }
                    }""")
            }

            val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempFilePath)!!
            val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as KtFile

            myFixture.openFileInEditor(virtualFile)
            assertTrue("File outside source set should be highlighted", ProblemHighlightFilter.shouldHighlightFile(ktFile))

            val highlightingLevelManager = HighlightingLevelManager.getInstance(project)
            assertFalse("Inspections should be skipped outside of a content root",
                highlightingLevelManager.shouldInspect(ktFile))
            DumbModeTestUtils.runInDumbModeSynchronously(project) {
                assertFalse(
                    "Inspections should be skipped outside of a content root during indexing",
                    highlightingLevelManager.shouldInspect(ktFile),
                )
            }

            val data = ExpectedHighlightingData(myFixture.editor.document, true, false, false)
            data.checkSymbolNames()
            data.init()
            (myFixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(data)
        }
    }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val path = Files.createTempDirectory("test")
        try {
            block(path)
        } finally {
            path.deleteRecursively()
        }
    }
}