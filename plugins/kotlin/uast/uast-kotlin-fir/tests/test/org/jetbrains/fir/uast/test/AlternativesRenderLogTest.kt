// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.DEFAULT_TYPES_LIST
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.test.env.kotlin.assertEqualsToFile
import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import org.junit.runner.RunWith
import java.nio.file.Path

@RunWith(JUnit3RunnerWithInners::class)
class AlternativesRenderLogTest : AbstractFirUastTest() {

    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    fun testClassAnnotation() = doCheck("ClassAnnotation.kt")

    fun testInnerClasses() = doCheck("InnerClasses.kt")

    fun testLocalDeclarations() = doCheck("LocalDeclarations.kt")

    fun testParameterPropertyWithAnnotation() = doCheck("ParameterPropertyWithAnnotation.kt")

    override fun check(filePath: String, file: UFile) {
        val valuesFile = testBasePath.resolve("${getTestName(false)}.altlog.txt")
        assertEqualsToFile("alternatives conversion result", valuesFile.toFile(), file.asMultiplesTargetConversionResult())
    }

    private fun UFile.asMultiplesTargetConversionResult(): String {
        val plugin = UastLanguagePlugin.byLanguage(KotlinLanguage.INSTANCE)!!
        val builder = StringBuilder()
        var level = 0
        (this.psi as KtFile).accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val uElement = plugin.convertToAlternatives(element, DEFAULT_TYPES_LIST).toList()

                if (uElement.any()) {
                    builder.append("    ".repeat(level))
                    builder.append("[${uElement.size}]:")
                    builder.append(uElement.joinToString(", ", "[", "]") { it.asLogString() })
                    builder.appendLine()
                }
                if (uElement.any()) level++
                element.acceptChildren(this)
                if (uElement.any()) level--
            }
        })
        return builder.toString()
    }
}
