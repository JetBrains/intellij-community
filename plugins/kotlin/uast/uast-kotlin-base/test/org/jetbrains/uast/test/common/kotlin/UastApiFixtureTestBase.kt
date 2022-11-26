// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import junit.framework.TestCase
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.uast.*
import org.jetbrains.uast.test.env.findElementByTextFromPsi

interface UastApiFixtureTestBase : UastPluginSelection {
    fun checkAssigningArrayElementType(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "MyClass.kt", """ 
            fun foo() {
                val arr = arrayOfNulls<List<*>>(10)
                arr[0] = emptyList<Any>()
                
                val lst = mutableListOf<List<*>>()
                lst[0] = emptyList<Any>()
            }
        """
        )

        val uFile = myFixture.file.toUElement()!!

        TestCase.assertEquals(
            "PsiType:List<?>",
            uFile.findElementByTextFromPsi<UExpression>("arr[0]").getExpressionType().toString()
        )
        TestCase.assertEquals(
            "PsiType:List<?>",
            uFile.findElementByTextFromPsi<UExpression>("lst[0]").getExpressionType().toString()
        )
    }

    fun checkDivByZero(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "MyClass.kt", """
            val p = 1 / 0
        """
        )

        val uFile = myFixture.file.toUElement()!!
        val p = uFile.findElementByTextFromPsi<UVariable>("p", strict = false)
        TestCase.assertNotNull("can't convert property p", p)
        TestCase.assertNotNull("can't find property initializer", p.uastInitializer)
        TestCase.assertNull("Should not see ArithmeticException", p.uastInitializer?.evaluate())
    }

    fun checkDetailsOfDeprecatedHidden(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "MyClass.kt", """
            @Deprecated(level = DeprecationLevel.WARNING, message="subject to change")
            fun test1() { }
            @Deprecated(level = DeprecationLevel.HIDDEN, message="no longer supported")
            fun test2() { }
            
            class Test(private val parameter: Int)  {
                @Deprecated(message = "Binary compatibility", level = DeprecationLevel.HIDDEN)
                constructor() : this(42)
            }
        """
        )

        val uFile = myFixture.file.toUElement()!!

        val test1 = uFile.findElementByTextFromPsi<UMethod>("test1", strict = false)
        TestCase.assertNotNull("can't convert function test1", test1)
        TestCase.assertTrue("Warning level, hasAnnotation", test1.javaPsi.hasAnnotation("kotlin.Deprecated"))
        TestCase.assertTrue("Warning level, isDeprecated", test1.javaPsi.isDeprecated)
        TestCase.assertTrue("Warning level, public", test1.javaPsi.hasModifierProperty(PsiModifier.PUBLIC))

        val test2 = uFile.findElementByTextFromPsi<UMethod>("test2", strict = false)
        TestCase.assertNotNull("can't convert function test2", test2)
        TestCase.assertTrue("Hidden level, hasAnnotation", test2.javaPsi.hasAnnotation("kotlin.Deprecated"))
        TestCase.assertTrue("Hidden level, isDeprecated", test2.javaPsi.isDeprecated)
        TestCase.assertTrue("Hidden level, public", test2.javaPsi.hasModifierProperty(PsiModifier.PUBLIC))

        val testClass = uFile.findElementByTextFromPsi<UClass>("Test", strict = false)
        TestCase.assertNotNull("can't convert class Test", testClass)
        testClass.methods.forEach { mtd ->
            if (mtd.sourcePsi is KtConstructor<*>) {
                TestCase.assertTrue("$mtd should be marked as a constructor", mtd.isConstructor)
            }
        }
    }

    fun checkImplicitReceiverType(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """
            public class MyBundle {
              public void putString(String key, String value) { }
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                fun foo() {
                  MyBundle().apply {
                    <caret>putString("k", "v")
                  }
                }
            """.trimIndent()
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        TestCase.assertEquals("putString", uCallExpression.methodName)
        TestCase.assertEquals("PsiType:MyBundle", uCallExpression.receiverType?.toString())
    }

    fun checkSubstitutedReceiverType(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                inline fun <T, R> T.use(block: (T) -> R): R {
                  return block(this)
                }
                
                fun foo() {
                  // T: String, R: Int
                  val len = "42".u<caret>se { it.length }
                }
            """.trimIndent()
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        TestCase.assertEquals("use", uCallExpression.methodName)
        TestCase.assertEquals("PsiType:String", uCallExpression.receiverType?.toString())
    }

    fun checkCallKindOfSamConstructor(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                val r = java.lang.Runnable { }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val uCallExpression = uFile.findElementByTextFromPsi<UCallExpression>("Runnable", strict = false)
            .orFail("cant convert to UCallExpression")
        TestCase.assertEquals("Runnable", uCallExpression.methodName)
        TestCase.assertEquals(UastCallKind.CONSTRUCTOR_CALL, uCallExpression.kind)
    }

    // Regression test from KTIJ-23503
    fun checkExpressionTypeFromIncorrectObject(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Outer() {
                    object { // <no name provided>
                        class Inner() {}

                        fun getInner() = Inner()
                    }
                }

                fun main(args: Array<String>) {
                    val inner = Outer.getInner()
                }
            """.trimIndent()
        )

        val errorType = "PsiType:<ErrorType>"
        val expectedPsiTypes = setOf("PsiType:Inner", errorType)
        myFixture.file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                // Mimic what [IconLineMarkerProvider#collectSlowLineMarkers] does.
                element.toUElementOfType<UCallExpression>()?.let {
                    val expressionType = it.getExpressionType()?.toString() ?: errorType
                    TestCase.assertTrue(expressionType in expectedPsiTypes)
                }

                super.visitElement(element)
            }
        })
    }

}