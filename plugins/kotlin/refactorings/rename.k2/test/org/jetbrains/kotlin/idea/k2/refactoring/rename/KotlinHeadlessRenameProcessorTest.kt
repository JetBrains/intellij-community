// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.HeadlessRenameProcessor
import com.intellij.refactoring.rename.HeadlessRenameResult
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.test.util.invalidateCaches
import java.io.File

class KotlinHeadlessRenameProcessorTest : KotlinLightCodeInsightFixtureTestCase() {

    override val testDataDirectory: File get() = KotlinRoot.DIR

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    fun testAccessorsOfAPropertyAreRenamed() {
        val counter = myFixture.addFileToProject(
            "Counter.kt",
            """
            class Counter {
                var hits: Int = 0
            }
            """.trimIndent(),
        )
        val caller = myFixture.addFileToProject(
            "Caller.java",
            """
            public class Caller {
              int read(Counter counter) {
                return counter.getHits();
              }
            }
            """.trimIndent(),
        )

        val result = performRename(findDeclaration(counter, "Counter", "hits"), "count")

        assertInstanceOf(result, HeadlessRenameResult.Applied::class.java)
        assertTrue("the property kept the old name: ${counter.text}", counter.text.contains("var count: Int"))
        assertTrue("the Java caller kept the old name: ${caller.text}", caller.text.contains("counter.getCount()"))
    }

    fun testBasePropertyAndItsOverrideAreRenamed() {
        val file = myFixture.addFileToProject(
            "Hierarchy.kt",
            """
            open class Base {
                open val hits: Int = 0
            }

            class Impl : Base() {
                override val hits: Int = 1
            }
            """.trimIndent(),
        )

        val result = performRename(findDeclaration(file, "Impl", "hits"), "count")

        assertInstanceOf(result, HeadlessRenameResult.Applied::class.java)
        assertTrue("the base property kept the old name: ${file.text}", file.text.contains("open val count: Int"))
        assertTrue("the override kept the old name: ${file.text}", file.text.contains("override val count: Int"))
    }

    fun testBaseFunctionAndItsOverrideAreRenamed() {
        val file = myFixture.addFileToProject(
            "Runner.kt",
            """
            open class Runner {
                open fun run() {}
            }

            class Worker : Runner() {
                override fun run() {}
            }
            """.trimIndent(),
        )

        val result = performRename(findDeclaration(file, "Worker", "run"), "execute")

        assertInstanceOf(result, HeadlessRenameResult.Applied::class.java)
        assertTrue("the base function kept the old name: ${file.text}", file.text.contains("open fun execute()"))
        assertTrue("the override kept the old name: ${file.text}", file.text.contains("override fun execute()"))
    }

    fun testFileOfAClassIsRenamed() {
        val file = myFixture.addFileToProject("Widget.kt", "class Widget")

        val result = performRename(findClass(file, "Widget"), "Gadget")

        assertInstanceOf(result, HeadlessRenameResult.Applied::class.java)
        assertEquals("the file kept the old name", "Gadget.kt", file.name)
        assertTrue("the class kept the old name: ${file.text}", file.text.contains("class Gadget"))
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun performRename(target: PsiElement, newName: String): HeadlessRenameResult = allowAnalysisOnEdt {
        val analysis = HeadlessRenameProcessor.analyze(project, target, newName)
        val planned = assertInstanceOf(analysis, HeadlessRenameResult.Planned::class.java)
        planned.plan.apply()
    }

    private fun findDeclaration(file: PsiFile, className: String, name: String): KtNamedDeclaration =
        findClass(file, className).declarations.filterIsInstance<KtNamedDeclaration>().first { it.name == name }

    private fun findClass(file: PsiFile, name: String): KtClass =
        PsiTreeUtil.findChildrenOfType(file, KtClass::class.java).first { it.name == name }
}
