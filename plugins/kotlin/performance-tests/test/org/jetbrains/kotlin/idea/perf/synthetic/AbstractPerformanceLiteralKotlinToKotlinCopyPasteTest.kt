// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.perf.synthetic

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.AbstractCopyPasteTest
import org.jetbrains.kotlin.idea.performance.tests.utils.dispatchAllInvocationEvents
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.testFramework.Stats
import org.jetbrains.kotlin.idea.testFramework.Stats.Companion.WARM_UP
import org.jetbrains.kotlin.idea.testFramework.performanceTest
import java.io.File

/**
 * Inspired by AbstractLiteralKotlinToKotlinCopyPasteTest
 */
abstract class AbstractPerformanceLiteralKotlinToKotlinCopyPasteTest : AbstractCopyPasteTest() {

    companion object {
        @JvmStatic
        var warmedUp: Boolean = false

        @JvmStatic
        val stats: Stats = Stats("Literal-k2k-CopyPaste")
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun setUp() {
        super.setUp()

        if (!warmedUp) {
            doWarmUpPerfTest()
            warmedUp = true
        }
    }

    override fun tearDown() {
        RunAll(
            ThrowableRunnable { super.tearDown() }
        ).run()
    }

    private fun doWarmUpPerfTest() {
        val fileEditorManager = FileEditorManager.getInstance(project)
        performanceTest<Pair<PsiFile, PsiFile>, Unit> {
            name(WARM_UP)
            stats(stats)
            iterations(1)
            setUp {
                val file1 = myFixture.configureByText(
                    "src.kt",
                    "class Foo {\n    <selection>private val value: String? = n<caret></selection>\n}"
                )
                val file2 = myFixture.configureByText("target.kt", "<caret>")
                it.setUpValue = Pair(file1, file2)
            }
            test {
                fileEditorManager.setSelectedEditor(it.setUpValue!!.first.virtualFile, "")
                myFixture.performEditorAction(IdeActions.ACTION_COPY)

                fileEditorManager.setSelectedEditor(it.setUpValue!!.second.virtualFile, "")
                myFixture.performEditorAction(IdeActions.ACTION_PASTE)

                dispatchAllInvocationEvents()
            }
            tearDown {
                assertEquals("private val value: String? = n", it.setUpValue!!.second.text)

                // to avoid VFS refresh
                myFixture.performEditorAction(IdeActions.ACTION_UNDO)

                runWriteAction {
                    it.setUpValue!!.first.delete()
                    it.setUpValue!!.second.delete()
                }
            }
        }
    }

    fun doPerfTest(unused: String) {
        val testName = getTestName(false)
        val fileName = fileName()
        val testPath: String = dataFilePath(fileName())
        val expectedPath = File(testPath.replace(".kt", ".expected.kt"))

        val fileEditorManager = FileEditorManager.getInstance(project)
        performanceTest<Array<PsiFile>, Unit> {
            name(testName)
            stats(stats)
            setUp {
                it.setUpValue = myFixture.configureByFiles(fileName, fileName.replace(".kt", ".to.kt"))
            }
            test {
                fileEditorManager.setSelectedEditor(it.setUpValue!![0].virtualFile, "")
                myFixture.performEditorAction(IdeActions.ACTION_COPY)

                fileEditorManager.setSelectedEditor(it.setUpValue!![1].virtualFile, "")
                myFixture.performEditorAction(IdeActions.ACTION_PASTE)

                dispatchAllInvocationEvents()
            }
            tearDown {
                KotlinTestUtils.assertEqualsToFile(expectedPath, it.setUpValue!![1].text)

                // to avoid VFS refresh
                myFixture.performEditorAction(IdeActions.ACTION_UNDO)

                runWriteAction {
                    it.setUpValue!!.forEach { f -> f.delete() }
                }
            }
        }
    }
}