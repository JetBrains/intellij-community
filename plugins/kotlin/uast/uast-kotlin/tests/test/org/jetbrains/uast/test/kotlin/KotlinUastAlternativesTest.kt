// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.test.kotlin

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.test.testFramework.KtUsefulTestCase.assertInstanceOf
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.*
import org.junit.Test

class KotlinUastAlternativesTest : AbstractKotlinUastTest() {
    override fun check(testName: String, file: UFile) { }

    private fun UFile.findIndexOfElement(elem: String): Int {
        val index = sourcePsi.text.indexOf(elem)
        if (index == -1) fail("Could not retrieve element $elem.")
        return index
    }

    @Test
    fun testPropertyAlternatives() {
        doTest("ManyAlternatives") { _, file ->
            val index = file.findIndexOfElement("writebleProp")
            val ktProperty = PsiTreeUtil.getParentOfType(file.sourcePsi.findElementAt(index), KtProperty::class.java)!!
            val plugin = UastLanguagePlugin.byLanguage(ktProperty.language)!!

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UField::class.java)).let {
                assertEquals(
                    "@org.jetbrains.annotations.NotNull private var writebleProp: int = 0",
                    it.joinToString(transform = UElement::asRenderString)
                )
            }

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UMethod::class.java, UField::class.java)).let {
                assertEquals(
                    "public final fun getWritebleProp() : int = UastEmptyExpression, " +
                            "public final fun setWritebleProp(<set-?>: int) : void = UastEmptyExpression, " +
                            "@org.jetbrains.annotations.NotNull private var writebleProp: int = 0",
                    it.joinToString(transform = UElement::asRenderString)
                )
            }

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UField::class.java, UMethod::class.java)).let {
                assertEquals(
                    "@org.jetbrains.annotations.NotNull private var writebleProp: int = 0, " +
                            "public final fun getWritebleProp() : int = UastEmptyExpression, " +
                            "public final fun setWritebleProp(<set-?>: int) : void = UastEmptyExpression",
                    it.joinToString(transform = UElement::asRenderString)
                )
            }

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UDeclaration::class.java)).let {
                assertEquals(
                    "@org.jetbrains.annotations.NotNull private var writebleProp: int = 0, " +
                            "public final fun getWritebleProp() : int = UastEmptyExpression, " +
                            "public final fun setWritebleProp(<set-?>: int) : void = UastEmptyExpression",
                    it.joinToString(transform = UElement::asRenderString)
                )
            }

        }
    }


    @Test
    fun testParamAndPropertylternatives() {
        doTest("ManyAlternatives") { _, file ->
            val index = file.findIndexOfElement("paramAndProp")
            val ktProperty = PsiTreeUtil.getParentOfType(file.sourcePsi.findElementAt(index), KtParameter::class.java)!!
            val plugin = UastLanguagePlugin.byLanguage(ktProperty.language)!!

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UField::class.java)).let {
                assertInstanceOf(it.single(), UField::class.java)
                assertEquals(
                    "@org.jetbrains.annotations.NotNull private final var paramAndProp: java.lang.String",
                    it.joinToString(transform = UElement::asRenderString)
                )
            }

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UParameter::class.java)).let {
                assertInstanceOf(it.single(), UParameter::class.java)
                assertEquals(
                    "@org.jetbrains.annotations.NotNull var paramAndProp: java.lang.String",
                    it.joinToString(transform = UElement::asRenderString)
                )
            }

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UElement::class.java)).let {
                assertEquals(
                    "@org.jetbrains.annotations.NotNull var paramAndProp: java.lang.String, " +
                            "@org.jetbrains.annotations.NotNull private final var paramAndProp: java.lang.String, " +
                            "public final fun getParamAndProp() : java.lang.String = UastEmptyExpression",
                    it.joinToString(transform = UElement::asRenderString)
                )
            }

        }
    }

    @Test
    fun testJustParamAlternatives() {
        doTest("ManyAlternatives") { _, file ->
            val index = file.findIndexOfElement("justParam")
            val ktProperty = PsiTreeUtil.getParentOfType(file.sourcePsi.findElementAt(index), KtParameter::class.java)!!
            val plugin = UastLanguagePlugin.byLanguage(ktProperty.language)!!

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UField::class.java)).let {
                assertEquals("", it.joinToString(transform = UElement::asRenderString))
            }

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UParameter::class.java)).let {
                assertInstanceOf(it.single(), UParameter::class.java)
                assertEquals(
                    "@org.jetbrains.annotations.NotNull var justParam: int",
                    it.joinToString(transform = UElement::asRenderString)
                )
            }

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UElement::class.java)).let {
                assertEquals(
                    "@org.jetbrains.annotations.NotNull var justParam: int",
                    it.joinToString(transform = UElement::asRenderString)
                )
            }

        }
    }

    @Test
    fun testPrimaryConstructorAlternatives() {
        doTest("ManyAlternatives") { _, file ->
            val index = file.findIndexOfElement("ClassA")
            val ktProperty = PsiTreeUtil.getParentOfType(file.sourcePsi.findElementAt(index), KtClass::class.java)!!
            val plugin = UastLanguagePlugin.byLanguage(ktProperty.language)!!

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UClass::class.java)).let {
                assertEquals("public final class ClassA {", it.joinToString { it.asRenderString().lineSequence().first() })
            }

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UClass::class.java, UMethod::class.java)).let {
                assertEquals(
                    "public final class ClassA {, " +
                            "public fun ClassA(@org.jetbrains.annotations.NotNull justParam: int, @org.jetbrains.annotations.NotNull paramAndProp: java.lang.String) = UastEmptyExpression",
                    it.joinToString { it.asRenderString().lineSequence().first() }
                )
            }

            plugin.convertToAlternatives<UElement>(ktProperty, arrayOf(UElement::class.java)).let {
                assertEquals(
                    "public final class ClassA {, " +
                            "public fun ClassA(@org.jetbrains.annotations.NotNull justParam: int, @org.jetbrains.annotations.NotNull paramAndProp: java.lang.String) = UastEmptyExpression",
                    it.joinToString { it.asRenderString().lineSequence().first() }
                )
            }

        }
    }

    @Test
    fun testStaticMethodAlternatives() {
        doTest("ManyAlternatives") { name, file ->
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
