// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.common.kotlin

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase.assertContainsElements
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase.assertDoesntContain
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.KotlinUFunctionCallExpression
import org.jetbrains.uast.test.env.findElementByText
import org.jetbrains.uast.test.env.findElementByTextFromPsi
import org.jetbrains.uast.test.env.findUElementByTextFromPsi

interface UastResolveApiFixtureTestBase : UastPluginSelection {
    fun checkResolveStringFromUast(myFixture: JavaCodeInsightTestFixture, project: Project) {
        val file = myFixture.addFileToProject(
            "s.kt", """fun foo(){
                val s = "abc"
                s.toUpperCase()
                }
            ""${'"'}"""
        )

        val refs = file.findUElementByTextFromPsi<UQualifiedReferenceExpression>("s.toUpperCase()")
        val receiver = refs.receiver
        TestCase.assertEquals(CommonClassNames.JAVA_LANG_STRING, (receiver.getExpressionType() as PsiClassType).resolve()!!.qualifiedName!!)
        val resolve = receiver.cast<UReferenceExpression>().resolve()

        val variable = file.findUElementByTextFromPsi<UVariable>("val s = \"abc\"")
        TestCase.assertEquals(resolve, variable.javaPsi)
        TestCase.assertTrue(
            "resolved expression $resolve should be equivalent to ${variable.sourcePsi}",
            PsiManager.getInstance(project).areElementsEquivalent(resolve, variable.sourcePsi)
        )
    }

    fun checkMultiResolve(myFixture: JavaCodeInsightTestFixture) {
        val file = myFixture.configureByText(
            "s.kt", """
                fun foo(): Int = TODO()
                fun foo(a: Int): Int = TODO()
                fun foo(a: Int, b: Int): Int = TODO()


                fun main(args: Array<String>) {
                    foo(1<caret>
                }"""
        )

        val main = file.toUElement()!!.findElementByTextFromPsi<UElement>("main").getContainingUMethod()!!
        val functionCall =
            main.findElementByText<UElement>("foo").uastParent as KotlinUFunctionCallExpression

        val resolvedDeclaration = functionCall.multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element.text ?: "<null>" }
        assertContainsElements(
            resolvedDeclarationsStrings,
            "fun foo(): Int = TODO()",
            "fun foo(a: Int): Int = TODO()",
            "fun foo(a: Int, b: Int): Int = TODO()"
        )

        TestCase.assertEquals(PsiType.INT, functionCall.getExpressionType())

        val firstArgument = main.findElementByText<UElement>("1")
        val firstParameter = functionCall.getArgumentForParameter(0)
        TestCase.assertEquals(firstArgument, firstParameter)

    }

    fun checkMultiResolveJava(myFixture: JavaCodeInsightTestFixture) {
        val file = myFixture.configureByText(
            "s.kt", """
                fun main(args: Array<String>) {
                    System.out.print("1"
                }
                """
        )

        val main = file.toUElement()!!.findElementByTextFromPsi<UElement>("main").getContainingUMethod()!!
        val functionCall = main.findElementByText<UElement>("print").uastParent as KotlinUFunctionCallExpression

        val resolvedDeclaration = functionCall.multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { r -> methodSignature(r.element) }
        assertContainsElements(
            resolvedDeclarationsStrings,
            "PsiType:void print(PsiType:boolean)",
            "PsiType:void print(PsiType:char)",
            "PsiType:void print(PsiType:int)",
            "PsiType:void print(PsiType:long)",
            "PsiType:void print(PsiType:float)",
            "PsiType:void print(PsiType:double)",
            "PsiType:void print(PsiType:char[])",
            "PsiType:void print(PsiType:String)",
            "PsiType:void print(PsiType:Object)"
        )

        TestCase.assertEquals("PsiType:Unit", functionCall.getExpressionType()?.toString())

        val firstArgument = main.findElementByText<UElement>("1")
        val firstParameter = functionCall.getArgumentForParameter(0)
        TestCase.assertEquals(firstArgument, firstParameter)
    }

    private fun methodSignature(psiMethod: PsiMethod): String {
        return "${psiMethod.returnType} ${psiMethod.name}(${psiMethod.parameterList.parameters.joinToString(", ") { it.type.toString() }})"
    }

    fun checkMultiResolveJavaAmbiguous(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """
            public class JavaClass {

                public void setParameter(String name, int value){}
                public void setParameter(String name, double value){}
                public void setParameter(String name, String value){}

            }
        """
        )
        val file = myFixture.configureByText(
            "s.kt", """

                fun main(args: Array<String>) {
                    JavaClass().setParameter("1"
                }
                """
        )

        val main = file.toUElement()!!.findElementByTextFromPsi<UElement>("main").getContainingUMethod()!!
        val functionCall = main.findElementByText<UElement>("setParameter").uastParent as KotlinUFunctionCallExpression

        val resolvedDeclaration = functionCall.multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element.text ?: "<null>" }
        assertContainsElements(
            resolvedDeclarationsStrings,
            "public void setParameter(String name, int value){}",
            "public void setParameter(String name, double value){}",
            "public void setParameter(String name, String value){}"

        )

        TestCase.assertEquals(PsiType.VOID, functionCall.getExpressionType())

        val firstArgument = main.findElementByText<UElement>("1")
        val firstParameter = functionCall.getArgumentForParameter(0)
        TestCase.assertEquals(firstArgument, firstParameter)
    }


    fun checkResolveFromBaseJava(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """public class X {
        |native String getFoo();
        |native void setFoo(@org.jetbrains.annotations.Nls String s);
        |}""".trimMargin()
        )
        myFixture.configureByText(
            "Foo.kt", """
               class Foo : X() {
               
                  fun foo(x : X) {
                    foo = "java superclass setter"
                    this.foo = "java superclass qualified setter"
                  }
                }
            """.trimIndent()
        )
        val main = myFixture.file.toUElement()!!.findElementByTextFromPsi<UElement>("foo").getContainingUMethod()!!
        main.findElementByText<UElement>("foo = \"java superclass setter\"")
            .cast<UBinaryExpression>().leftOperand.cast<UReferenceExpression>().let { assignment ->
                val resolvedDeclaration = assignment.resolve()
                KotlinLightCodeInsightFixtureTestCaseBase.assertEquals(
                    "native void setFoo(@org.jetbrains.annotations.Nls String s);",
                    resolvedDeclaration?.text
                )
            }
        main.findElementByText<UElement>("this.foo = \"java superclass qualified setter\"")
            .cast<UBinaryExpression>().leftOperand.cast<UReferenceExpression>().let { assignment ->
                val resolvedDeclaration = assignment.resolve()
                KotlinLightCodeInsightFixtureTestCaseBase.assertEquals(
                    "native void setFoo(@org.jetbrains.annotations.Nls String s);",
                    resolvedDeclaration?.text
                )
            }
    }

    fun checkMultiResolveInClass(myFixture: JavaCodeInsightTestFixture) {
        val file = myFixture.configureByText(
            "s.kt", """
                class MyClass {

                    fun foo(): Int = TODO()
                    fun foo(a: Int): Int = TODO()
                    fun foo(a: Int, b: Int): Int = TODO()

                }

                fun foo(string: String) = TODO()


                fun main(args: Array<String>) {
                    MyClass().foo(
                }
            """
        )


        val functionCall =
            file.toUElement()!!.findElementByTextFromPsi<UElement>("main").getContainingUMethod()!!
                .findElementByText<UElement>("foo").uastParent as KotlinUFunctionCallExpression

        val resolvedDeclaration = functionCall.multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element.text ?: "<null>" }
        assertContainsElements(
            resolvedDeclarationsStrings,
            "fun foo(): Int = TODO()",
            "fun foo(a: Int): Int = TODO()",
            "fun foo(a: Int, b: Int): Int = TODO()"
        )
        assertDoesntContain(resolvedDeclarationsStrings, "fun foo(string: String) = TODO()")
        TestCase.assertEquals(PsiType.INT, functionCall.getExpressionType())
    }

    fun checkMultiConstructorResolve(myFixture: JavaCodeInsightTestFixture, project: Project) {
        val file = myFixture.configureByText(
            "s.kt", """
                class MyClass(int: Int) {

                    constructor(int: Int, int1: Int) : this(int + int1)

                    fun foo(): Int = TODO()

                }

                fun MyClass(string: String): MyClass = MyClass(1)


                fun main(args: Array<String>) {
                    MyClass(
                }
            """
        )


        val functionCall =
            file.toUElement()!!.findElementByTextFromPsi<UElement>("main").getContainingUMethod()!!
                .findElementByText<UElement>("MyClass").uastParent as KotlinUFunctionCallExpression

        val resolvedDeclaration = functionCall.multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element.text ?: "<null>" }
        assertContainsElements(
            resolvedDeclarationsStrings,
            "(int: Int)",
            "constructor(int: Int, int1: Int) : this(int + int1)",
            "fun MyClass(string: String): MyClass = MyClass(1)"
        )
        assertDoesntContain(resolvedDeclarationsStrings, "fun foo(): Int = TODO()")
        TestCase.assertEquals(PsiType.getTypeByName("MyClass", project, file.resolveScope), functionCall.getExpressionType())
    }


    fun checkMultiInvokableObjectResolve(myFixture: JavaCodeInsightTestFixture) {
        val file = myFixture.configureByText(
            "s.kt", """
                object Foo {

                    operator fun invoke(i: Int): Int = TODO()
                    operator fun invoke(i1: Int, i2: Int): Int = TODO()
                    operator fun invoke(i1: Int, i2: Int, i3: Int): Int = TODO()

                }

                fun main(args: Array<String>) {
                    Foo(
                }
            """
        )

        val functionCall =
            file.toUElement()!!.findElementByTextFromPsi<UElement>("main").getContainingUMethod()!!
                .findElementByText<UElement>("Foo").uastParent as KotlinUFunctionCallExpression

        val resolvedDeclaration = functionCall.multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element.text ?: "<null>" }
        assertContainsElements(
            resolvedDeclarationsStrings,
            "operator fun invoke(i: Int): Int = TODO()",
            "operator fun invoke(i1: Int, i2: Int): Int = TODO()",
            "operator fun invoke(i1: Int, i2: Int, i3: Int): Int = TODO()"
        )
        TestCase.assertEquals(PsiType.INT, functionCall.getExpressionType())
    }

    fun checkMultiResolveJvmOverloads(myFixture: JavaCodeInsightTestFixture) {
        val file = myFixture.configureByText(
            "s.kt", """

                class MyClass {

                    @JvmOverloads
                    fun foo(i1: Int = 1, i2: Int = 2): Int = TODO()

                }

                fun main(args: Array<String>) {
                    MyClass().foo(
                }"""
        )

        val functionCall =
            file.toUElement()!!.findElementByTextFromPsi<UElement>("main").getContainingUMethod()!!
                .findElementByText<UElement>("foo").uastParent as KotlinUFunctionCallExpression

        val resolvedDeclaration = functionCall.multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element.text ?: "<null>" }
        assertContainsElements(
            resolvedDeclarationsStrings,
            "@JvmOverloads\n                    fun foo(i1: Int = 1, i2: Int = 2): Int = TODO()"
        )
        TestCase.assertEquals(PsiType.INT, functionCall.getExpressionType())
    }

    fun checkLocalResolve(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "MyClass.kt", """
            fun foo() {
                fun bar() {}
                
                ba<caret>r()
            }
        """
        )


        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val resolved = uCallExpression.resolve()
            .orFail("cant resolve from $uCallExpression")
        TestCase.assertEquals("bar", resolved.name)
    }


    fun checkResolveCompiledAnnotation(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "MyClass.kt", """
            @Deprecated(message = "deprecated")    
            fun foo() {}
        """
        )

        val compiledAnnotationParameter = myFixture.file.toUElement()!!.findElementByTextFromPsi<USimpleNameReferenceExpression>("message")
        val resolved = (compiledAnnotationParameter.resolve() as? PsiMethod)
            .orFail("cant resolve annotation parameter")
        TestCase.assertEquals("message", resolved.name)
    }

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
        val p = uFile.findElementByTextFromPsi<UVariable>("p")
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
        """
        )

        val uFile = myFixture.file.toUElement()!!

        val test1 = uFile.findElementByTextFromPsi<UMethod>("test1")
        TestCase.assertNotNull("can't convert function test1", test1)
        TestCase.assertTrue("Warning level, hasAnnotation", test1.javaPsi.hasAnnotation("kotlin.Deprecated"))
        TestCase.assertTrue("Warning level, isDeprecated", test1.javaPsi.isDeprecated)
        TestCase.assertTrue("Warning level, public", test1.javaPsi.hasModifierProperty(PsiModifier.PUBLIC))

        val test2 = uFile.findElementByTextFromPsi<UMethod>("test2")
        TestCase.assertNotNull("can't convert function test2", test2)
        TestCase.assertTrue("Hidden level, hasAnnotation", test2.javaPsi.hasAnnotation("kotlin.Deprecated"))
        TestCase.assertTrue("Hidden level, isDeprecated", test2.javaPsi.isDeprecated)
        // KTIJ-18720
        TestCase.assertFalse("Hidden level, public", test2.javaPsi.hasModifierProperty(PsiModifier.PUBLIC))
    }

}