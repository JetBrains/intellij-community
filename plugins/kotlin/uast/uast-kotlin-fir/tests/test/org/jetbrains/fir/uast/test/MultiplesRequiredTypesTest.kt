// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.test.env.kotlin.assertEqualsToFile
import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import java.nio.file.Path

class MultiplesRequiredTypesTest : AbstractFirUastTest() {

    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    fun testInnerClasses() = doCheck("InnerClasses.kt")

    override fun check(filePath: String, file: UFile) {
        val valuesFile = testBasePath.resolve("${getTestName(false)}.splog.txt").toFile()
        assertEqualsToFile("MultiplesTargetConversionResult", valuesFile, file.asMultiplesTargetConversionResult())
    }

    private fun UFile.asMultiplesTargetConversionResult(): String {
        val plugin = UastLanguagePlugin.byLanguage(KotlinLanguage.INSTANCE)!!
        val builder = StringBuilder()
        var level = 0
        @Suppress("DEPRECATION")
        (this.psi as KtFile).accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val uElement =
                    plugin.convertElementWithParent(
                        element,
                        arrayOf(UFile::class.java, UClass::class.java, UField::class.java, UMethod::class.java)
                    )
                if (uElement != null) {
                    builder.append("    ".repeat(level))
                    builder.append(uElement.asLogString())
                    builder.appendLine()
                }
                if (uElement != null) level++
                element.acceptChildren(this)
                if (uElement != null) level--
            }
        })
        return builder.toString()
    }
}
