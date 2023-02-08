// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import junit.framework.TestCase
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.uast.*
import org.jetbrains.uast.test.env.findElementByTextFromPsi
import org.jetbrains.uast.visitor.AbstractUastVisitor

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
            "java.util.List<?>",
            uFile.findElementByTextFromPsi<UExpression>("arr[0]").getExpressionType()?.canonicalText
        )
        TestCase.assertEquals(
            "java.util.List<?>",
            uFile.findElementByTextFromPsi<UExpression>("lst[0]").getExpressionType()?.canonicalText
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
            // Example from KTIJ-18039
            "MyClass.kt", """
            @Deprecated(level = DeprecationLevel.WARNING, message="subject to change")
            fun test1() { }
            @Deprecated(level = DeprecationLevel.HIDDEN, message="no longer supported")
            fun test2() = Test(22)
            
            class Test(private val parameter: Int)  {
                @Deprecated(message = "Binary compatibility", level = DeprecationLevel.HIDDEN)
                constructor() : this(42)
            }
        """
        )

        val uFile = myFixture.file.toUElement()!!

        val test1 = uFile.findElementByTextFromPsi<UMethod>("test1", strict = false)
        TestCase.assertNotNull("can't convert function test1", test1)
        // KTIJ-18716
        TestCase.assertTrue("Warning level, hasAnnotation", test1.javaPsi.hasAnnotation("kotlin.Deprecated"))
        // KTIJ-18039
        TestCase.assertTrue("Warning level, isDeprecated", test1.javaPsi.isDeprecated)
        // KTIJ-18720
        TestCase.assertTrue("Warning level, public", test1.javaPsi.hasModifierProperty(PsiModifier.PUBLIC))
        // KTIJ-23807
        TestCase.assertTrue("Warning level, nullability", test1.javaPsi.annotations.none { it.isNullnessAnnotation })

        val test2 = uFile.findElementByTextFromPsi<UMethod>("test2", strict = false)
        TestCase.assertNotNull("can't convert function test2", test2)
        // KTIJ-18716
        TestCase.assertTrue("Hidden level, hasAnnotation", test2.javaPsi.hasAnnotation("kotlin.Deprecated"))
        // KTIJ-18039
        TestCase.assertTrue("Hidden level, isDeprecated", test2.javaPsi.isDeprecated)
        // KTIJ-18720
        TestCase.assertTrue("Hidden level, public", test2.javaPsi.hasModifierProperty(PsiModifier.PUBLIC))
        // KTIJ-23807
        TestCase.assertNotNull("Hidden level, nullability", test2.javaPsi.annotations.singleOrNull { it.isNullnessAnnotation })

        val testClass = uFile.findElementByTextFromPsi<UClass>("Test", strict = false)
        TestCase.assertNotNull("can't convert class Test", testClass)
        testClass.methods.forEach { mtd ->
            if (mtd.sourcePsi is KtConstructor<*>) {
                // KTIJ-20200
                TestCase.assertTrue("$mtd should be marked as a constructor", mtd.isConstructor)
            }
        }
    }

    fun checkTypesOfDeprecatedHidden(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                interface State<out T> {
                    val value: T
                }

                @Deprecated(level = DeprecationLevel.HIDDEN, message="no longer supported")
                fun before(
                    i : Int?,
                    s : String?,
                    vararg vs : Any,
                ): State<String> {
                    return object : State<String> {
                        override val value: String = i?.toString() ?: s ?: "42"
                    }
                }
                
                fun after(
                    i : Int?,
                    s : String?,
                    vararg vs : Any,
                ): State<String> {
                    return object : State<String> {
                        override val value: String = i?.toString() ?: s ?: "42"
                    }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        val before = uFile.findElementByTextFromPsi<UMethod>("before", strict = false)
            .orFail("cant convert to UMethod: before")
        val after = uFile.findElementByTextFromPsi<UMethod>("after", strict = false)
            .orFail("cant convert to UMethod: after")

        TestCase.assertEquals("return type", after.returnType, before.returnType)

        TestCase.assertEquals(after.uastParameters.size, before.uastParameters.size)
        after.uastParameters.zip(before.uastParameters).forEach { (afterParam, beforeParam) ->
            val paramName = afterParam.name
            TestCase.assertEquals(paramName, beforeParam.name)
            TestCase.assertEquals(paramName, afterParam.isVarArgs, beforeParam.isVarArgs)
            TestCase.assertEquals(paramName, afterParam.type, beforeParam.type)
            TestCase.assertEquals(
                paramName,
                (afterParam.javaPsi as PsiModifierListOwner).hasAnnotation(Nullable::class.java.name),
                (beforeParam.javaPsi as PsiModifierListOwner).hasAnnotation(Nullable::class.java.name)
            )
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
        TestCase.assertEquals("MyBundle", uCallExpression.receiverType?.canonicalText)
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
        TestCase.assertEquals("java.lang.String", uCallExpression.receiverType?.canonicalText)
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

        val errorType = "<ErrorType>"
        val expectedPsiTypes = setOf("Outer.Inner", errorType)
        myFixture.file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                // Mimic what [IconLineMarkerProvider#collectSlowLineMarkers] does.
                element.toUElementOfType<UCallExpression>()?.let {
                    val expressionType = it.getExpressionType()?.canonicalText ?: errorType
                    TestCase.assertTrue(
                        expressionType,
                        expressionType in expectedPsiTypes ||
                                // FE1.0 outputs Outer.no_name_in_PSI_hashcode.Inner
                                (expressionType.startsWith("Outer.") && expressionType.endsWith(".Inner"))
                    )
                }

                super.visitElement(element)
            }
        })
    }

    fun checkFlexibleFunctionalInterfaceType(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """
                package test.pkg;
                public interface ThrowingRunnable {
                    void run() throws Throwable;
                }
            """.trimIndent()
        )
        myFixture.addClass(
            """
                package test.pkg;
                public class Assert {
                    public static <T extends Throwable> T assertThrows(Class<T> expectedThrowable, ThrowingRunnable runnable) {}
                }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                import test.pkg.Assert

                fun dummy() = Any()
                
                fun test() {
                    Assert.assertThrows(Throwable::class.java) { dummy() }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val uLambdaExpression = uFile.findElementByTextFromPsi<ULambdaExpression>("{ dummy() }")
            .orFail("cant convert to ULambdaExpression")
        TestCase.assertEquals("test.pkg.ThrowingRunnable", uLambdaExpression.functionalInterfaceType?.canonicalText)
    }

    fun checkInvokedLambdaBody(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Lambda {
                  fun unusedLambda(s: String, o: Any) {
                    {
                      s === o
                      o.toString()
                      s.length
                    }
                  }

                  fun invokedLambda(s: String, o: Any) {
                    {
                      s === o
                      o.toString()
                      s.length
                    }()
                  }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        var unusedLambda: ULambdaExpression? = null
        var invokedLambda: ULambdaExpression? = null
        uFile.accept(object : AbstractUastVisitor() {
            private var containingUMethod: UMethod? = null

            override fun visitMethod(node: UMethod): Boolean {
                containingUMethod = node
                return super.visitMethod(node)
            }

            override fun afterVisitMethod(node: UMethod) {
                containingUMethod = null
                super.afterVisitMethod(node)
            }

            override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                if (containingUMethod?.name == "unusedLambda") {
                    unusedLambda = node
                }

                return super.visitLambdaExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName != OperatorNameConventions.INVOKE.identifier) return super.visitCallExpression(node)

                val receiver = node.receiver
                TestCase.assertNotNull(receiver)
                TestCase.assertTrue(receiver is ULambdaExpression)
                invokedLambda = receiver as ULambdaExpression

                return super.visitCallExpression(node)
            }
        })
        TestCase.assertNotNull(unusedLambda)
        TestCase.assertNotNull(invokedLambda)
        TestCase.assertEquals(unusedLambda!!.asRecursiveLogString(), invokedLambda!!.asRecursiveLogString())
    }

}