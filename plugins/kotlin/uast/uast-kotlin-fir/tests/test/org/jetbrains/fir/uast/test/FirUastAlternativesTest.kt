// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastLanguagePlugin

class FirUastAlternativesTest : AbstractFirUastTest() {
    override val isFirUastPlugin: Boolean = true

    override fun isFirPlugin(): Boolean = true

    override fun check(filePath: String, file: UFile) { }

    private fun UFile.findIndexOfElement(elem: String): Int {
        val index = sourcePsi.text.indexOf(elem)
        if (index == -1) fail("Could not retrieve element $elem.")
        return index
    }

    fun testStaticMethodAlternatives() {
        doCheck("../../uast-kotlin/tests/testData/ManyAlternatives.kt") { name, file ->
            val index = file.findIndexOfElement("foo")
            val ktFunction = PsiTreeUtil.getParentOfType(file.sourcePsi.findElementAt(index), KtNamedFunction::class.java)!!
            val plugin = UastLanguagePlugin.byLanguage(ktFunction.language)!!
            val alternatives = plugin.convertToAlternatives<UElement>(ktFunction, arrayOf(UMethod::class.java, UMethod::class.java))
            assertEquals("""
                @kotlin.jvm.JvmStatic
                public final fun foo() : void {
                }, public static final fun foo() : void {
                }
            """.trimIndent(), alternatives.joinToString(transform = UElement::asRenderString).replace("\r", ""))
        }
    }
}
