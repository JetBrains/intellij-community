// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.common.kotlin

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.replaceService
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.builtins.StandardNames.ENUM_VALUES
import org.jetbrains.kotlin.builtins.StandardNames.ENUM_VALUE_OF
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase.assertContainsElements
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase.assertDoesntContain
import org.jetbrains.kotlin.idea.test.util.JUnit4Assertions.assertSameElements
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtParameter
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

    fun checkResolveToFacade(myFixture: JavaCodeInsightTestFixture) {

        myFixture.project.replaceService(
            KotlinAsJavaSupport::class.java,
            object : MockKotlinAsJavaSupport(getInstance(myFixture.project)) {
                override fun getFacadeClasses(facadeFqName: FqName, scope: GlobalSearchScope): Collection<KtLightClassForFacade> =
                    // emulating facade classes from different modules
                    super.getFacadeClasses(facadeFqName, scope).let { it + it }
            },
            myFixture.testRootDisposable
        )

        myFixture.addFileToProject(
            "pkg/MyFacade.java", """
                package pkg;
                
                public class MyFacade {
                    public void bar(){}
                }
        """.trimIndent()
        )


        for (i in 1..3) {
            myFixture.addFileToProject(
                "pkg/mffacade$i.kt", """
                    @file:JvmMultifileClass
                    @file:JvmName("MyFacade")
                    package pkg;
                    
                    fun foo$i(vararg args: Int) = TODO()    
                """.trimIndent()
            )
        }

        val file = myFixture.configureByText(
            "s.kt", """
                import pkg.*
                
                fun main(args: Array<String>) {
                    foo1(1,2,3)
                    foo2(1,2,3)
                    foo3(1,2,3)
                }
            """
        )

        val main = file.toUElement()!!.findElementByTextFromPsi<UElement>("main").getContainingUMethod()!!
        val functionCalls = (1..3).map { i ->
            main.findElementByText<UElement>("foo$i").uastParent as KotlinUFunctionCallExpression
        }

        UsefulTestCase.assertSameElements(
            functionCalls
                .map { it.resolve()?.text ?: "<null>" },
            "fun foo1(vararg args: Int) = TODO()",
            "fun foo2(vararg args: Int) = TODO()",
            "fun foo3(vararg args: Int) = TODO()"
        )
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

    fun checkResolveLocalDefaultConstructor(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "MyClass.kt", """
            fun foo() {
                class LocalClass

                val lc = Local<caret>Class()
            }
        """
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val resolved = uCallExpression.resolve()
            .orFail("cant resolve from $uCallExpression")
        TestCase.assertTrue("Not resolved to local class default constructor", resolved.isConstructor)
        TestCase.assertEquals("LocalClass", resolved.name)
    }

    fun checkResolveJavaClassAsAnonymousObjectSuperType(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """public class JavaClass { }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                fun foo() {
                    val o = object : JavaClass() { }
                }
            """.trimIndent()
        )

        val o = myFixture.file.toUElement()!!.findElementByTextFromPsi<UObjectLiteralExpression>("object : JavaClass() { }")
        val resolved = (o.classReference?.resolve() as? PsiClass)
            .orFail("cant resolve Java class as a super type of an anonymous object")
        TestCase.assertEquals("JavaClass", resolved.name)
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

    fun checkResolveExplicitLambdaParameter(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                inline fun <T, R> T.use(block: (T) -> R): R {
                  return block(this)
                }
                
                fun foo() {
                  42.use { it ->
                    i<caret>t.toString()
                  }
                }
            """.trimIndent()
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        TestCase.assertEquals("it", (uCallExpression.receiver as? USimpleNameReferenceExpression)?.identifier)
        // Expect to be resolved to source KtParameter
        val resolved = (uCallExpression.receiver?.tryResolve() as? KtParameter)
            .orFail("cant resolve explicit lambda parameter")
        TestCase.assertEquals("it", resolved.name)
    }

    fun checkResolveImplicitLambdaParameter(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                inline fun <T, R> T.use(block: (T) -> R): R {
                  return block(this)
                }
                
                fun foo() {
                  42.use { i<caret>t.toString() }
                }
            """.trimIndent()
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        TestCase.assertEquals("it", (uCallExpression.receiver as? USimpleNameReferenceExpression)?.identifier)
        // No source for implicit lambda parameter. Expect to be resolved to fake PsiParameter used inside ULambdaExpression
        val resolved = (uCallExpression.receiver?.tryResolve() as? PsiParameter)
            .orFail("cant resolve implicit lambda parameter")
        TestCase.assertEquals("it", resolved.name)
    }

    fun checkResolveSyntheticMethod(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "MyClass.kt", """
            annotation class MyAnnotation

            class Foo {
                @MyAnnotation
                @JvmSynthetic
                fun bar() {}
            }

            fun test() {
                Foo().ba<caret>r()
            }
        """
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val resolved = uCallExpression.resolve()
            .orFail("cant resolve from $uCallExpression")
        TestCase.assertEquals("bar", resolved.name)

        TestCase.assertEquals(2, resolved.annotations.size)
        TestCase.assertTrue(resolved.hasAnnotation("MyAnnotation"))
        TestCase.assertTrue(resolved.hasAnnotation("kotlin.jvm.JvmSynthetic"))
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

    fun checkArgumentMappingDefaultValue(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
            class Foo {
                fun boo(ch: Char, i: Int = 42): Int {
                  return i
                }
            }
            
            fun box(foo: Foo) = run {
                foo.b<caret>oo('x')
            }
        """
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val resolved = uCallExpression.resolve()
            .orFail("cant resolve from $uCallExpression")

        resolved.parameters.forEachIndexed { index, _ ->
            val arg = uCallExpression.getArgumentForParameter(index)
            if (index == 0) {
                TestCase.assertNotNull("Value parameter ch of function boo", arg)
                TestCase.assertEquals('x', arg?.evaluate())
            } else {
                // 2nd parameter has a default value, and is not passed.
                TestCase.assertNull(arg)
            }
        }
    }

    fun checkArgumentMappingExtensionFunction(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
            class Foo {
            }
            
            fun Foo.boo(ch: Char): Int {
                return 42
            }
            
            fun box(foo: Foo) = run {
                foo.b<caret>oo('x')
            }
        """
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val resolved = uCallExpression.resolve()
            .orFail("cant resolve from $uCallExpression")

        resolved.parameters.forEachIndexed { index, _ ->
            val arg = uCallExpression.getArgumentForParameter(index)
            if (index == 0) {
                // Extension receiver parameter
                TestCase.assertNotNull("Extension receiver parameter", arg)
                TestCase.assertEquals("foo", (arg as? USimpleNameReferenceExpression)?.identifier)
            } else {
                // one and only parameter becomes 2nd parameter in JVM bytecode.
                TestCase.assertNotNull("Value parameter ch of function boo", arg)
                TestCase.assertEquals('x', arg?.evaluate())
            }
        }
    }

    fun checkArgumentMappingVararg(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
            class Foo {
                fun boo(vararg chars: Char): Int {
                  return chars.size
                }
            }
            
            fun box(foo: Foo) = run {
                foo.b<caret>oo('x', 'y', 'z')
            }
        """
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val resolved = uCallExpression.resolve()
            .orFail("cant resolve from $uCallExpression")

        resolved.parameters.forEachIndexed { index, _ ->
            val arg = uCallExpression.getArgumentForParameter(index)
            TestCase.assertNotNull("vararg chars of function boo", arg)
            TestCase.assertTrue(arg is UExpressionList)
            val varargValues = (arg as UExpressionList).expressions.map { it.evaluate() }
            assertSameElements(listOf('x', 'y', 'z'), varargValues) {
                varargValues.joinToString(separator = ", ", prefix = "[", postfix = "]")
            }
        }
    }

    fun checkArgumentMappingOOBE(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
            class Foo {
                suspend fun boo(ch: Char): Int {
                  return 42
                }
            }
            
            fun box(foo: Foo) = run {
                foo.b<caret>oo('x')
            }
        """
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val resolved = uCallExpression.resolve()
            .orFail("cant resolve from $uCallExpression")

        resolved.parameters.forEachIndexed { index, _ ->
            val arg = uCallExpression.getArgumentForParameter(index)
            if (index == 0) {
                TestCase.assertNotNull("Value parameter ch of function boo", arg)
                TestCase.assertEquals('x', arg?.evaluate())
            } else {
                // That suspend function has only one parameter. Anything else is generated by the compiler.
                // But, at least, UCallExpression#getArgumentForParameter should not raise an out-of-bound exception.
                TestCase.assertNull(arg)
            }
        }
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

    fun checkSyntheticEnumMethods(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "MyClass.kt", """
            enum class MyEnum  {
                FOO,
                BAR;
            }
            
            fun testValueOf() {
              MyEnum.valueOf("FOO")
            }
            
            fun testValues() {
              MyEnum.values()
            }
        """
        )

        val uFile = myFixture.file.toUElement()!!
        val myEnum = uFile.findElementByTextFromPsi<UClass>("MyEnum", strict = false)
        TestCase.assertNotNull("can't convert enum class MyEnum", myEnum)

        val syntheticMethods = setOf(ENUM_VALUES.identifier, ENUM_VALUE_OF.identifier)
        var metValues = false
        var metValueOf = false
        myEnum.methods.forEach { mtd ->
            if (!mtd.isConstructor) {
                TestCase.assertNotNull("Null return type of $mtd", mtd.returnType)
            }
            if (mtd.name in syntheticMethods) {
                when (mtd.name) {
                    ENUM_VALUES.identifier -> metValues = true
                    ENUM_VALUE_OF.identifier -> metValueOf = true
                }
                TestCase.assertTrue(
                    "Missing nullness annotations on $mtd",
                    mtd.javaPsi.modifierList.annotations.any { it.isNullnessAnnotation }
                )
            }
        }
        TestCase.assertTrue("Expect to meet synthetic values() methods in an enum class", metValues)
        TestCase.assertTrue("Expect to meet synthetic valueOf(String) methods in an enum class", metValueOf)

        val testValueOf = uFile.findElementByTextFromPsi<UMethod>("testValueOf", strict = false)
        TestCase.assertNotNull("testValueOf should be successfully converted", testValueOf)
        val valueOfCall = testValueOf.findElementByText<UElement>("valueOf").uastParent as KotlinUFunctionCallExpression
        val resolvedValueOfCall = valueOfCall.resolve()
        TestCase.assertNotNull("Unresolved MyEnum.valueOf(String)", resolvedValueOfCall)
        TestCase.assertNotNull("Null return type of $resolvedValueOfCall", resolvedValueOfCall?.returnType)
        TestCase.assertTrue(
            "Missing nullness annotations on $resolvedValueOfCall",
            resolvedValueOfCall!!.annotations.any { it.isNullnessAnnotation }
        )

        val testValues = uFile.findElementByTextFromPsi<UMethod>("testValues", strict = false)
        TestCase.assertNotNull("testValues should be successfully converted", testValues)
        val valuesCall = testValues.findElementByText<UElement>("values").uastParent as KotlinUFunctionCallExpression
        val resolvedValuesCall = valuesCall.resolve()
        TestCase.assertNotNull("Unresolved MyEnum.values()", resolvedValuesCall)
        TestCase.assertNotNull("Null return type of $resolvedValuesCall", resolvedValuesCall?.returnType)
        TestCase.assertTrue(
            "Missing nullness annotations on $resolvedValuesCall",
            resolvedValuesCall!!.annotations.any { it.isNullnessAnnotation }
        )
    }

    private val PsiAnnotation.isNullnessAnnotation: Boolean
        get() {
            return qualifiedName?.endsWith("NotNull") == true || qualifiedName?.endsWith("Nullable") == true
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

}
