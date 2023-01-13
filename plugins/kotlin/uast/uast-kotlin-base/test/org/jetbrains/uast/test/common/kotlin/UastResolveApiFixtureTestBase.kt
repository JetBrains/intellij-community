// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.kotlin.idea.base.test.JUnit4Assertions.assertSameElements
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.uast.*
import org.jetbrains.uast.test.env.findElementByText
import org.jetbrains.uast.test.env.findElementByTextFromPsi
import org.jetbrains.uast.test.env.findUElementByTextFromPsi
import org.jetbrains.uast.visitor.AbstractUastVisitor

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
            main.findElementByText<UElement>("foo").uastParent as UCallExpression

        val resolvedDeclaration = (functionCall as UMultiResolvable).multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element?.text ?: "<null>" }
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
        val functionCall = main.findElementByText<UElement>("print").uastParent as UCallExpression

        val resolvedDeclaration = (functionCall as UMultiResolvable).multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.mapNotNull { r ->
            (r.element as? PsiMethod)?.let { methodSignature(it) }
        }
        assertContainsElements(
            resolvedDeclarationsStrings,
            "void print(boolean)",
            "void print(char)",
            "void print(int)",
            "void print(long)",
            "void print(float)",
            "void print(double)",
            "void print(char[])",
            "void print(java.lang.String)",
            "void print(java.lang.Object)"
        )

        TestCase.assertEquals("kotlin.Unit", functionCall.getExpressionType()?.canonicalText)

        val firstArgument = main.findElementByText<UElement>("1")
        val firstParameter = functionCall.getArgumentForParameter(0)
        TestCase.assertEquals(firstArgument, firstParameter)
    }

    private fun methodSignature(psiMethod: PsiMethod): String {
        return buildString {
            append(psiMethod.returnType?.canonicalText)
            append(" ")
            append(psiMethod.name)
            append("(")
            psiMethod.parameterList.parameters.joinTo(this, separator = ", ") { it.type.canonicalText }
            append(")")
        }
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
        val functionCall = main.findElementByText<UElement>("setParameter").uastParent as UCallExpression

        val resolvedDeclaration = (functionCall as UMultiResolvable).multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element?.text ?: "<null>" }
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
                .findElementByText<UElement>("foo").uastParent as UCallExpression

        val resolvedDeclaration = (functionCall as UMultiResolvable).multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element?.text ?: "<null>" }
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
            main.findElementByText<UElement>("foo$i").uastParent as UCallExpression
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
                .findElementByText<UElement>("MyClass").uastParent as UCallExpression

        val resolvedDeclaration = (functionCall as UMultiResolvable).multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element?.text ?: "<null>" }
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
                .findElementByText<UElement>("Foo").uastParent as UCallExpression

        val resolvedDeclaration = (functionCall as UMultiResolvable).multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element?.text ?: "<null>" }
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
                .findElementByText<UElement>("foo").uastParent as UCallExpression

        val resolvedDeclaration = (functionCall as UMultiResolvable).multiResolve()
        val resolvedDeclarationsStrings = resolvedDeclaration.map { it.element?.text ?: "<null>" }
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
        val resolved = (uCallExpression.receiver?.tryResolve() as? PsiParameter)
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

    fun checkMapFunctions(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
            fun foo(map: MutableMap<String, String>) {
              map.getOrDefault("a", null)
              map.getOrDefault("a", "b")
              map.remove("a", "b")
            }
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElement()!!

        // https://issuetracker.google.com/234358370 (null key)
        // https://issuetracker.google.com/221280939 (null default value)
        val getOrDefaultExt = uFile.findElementByTextFromPsi<UCallExpression>("getOrDefault(\"a\", null)", strict = false)
            .orFail("cant convert to UCallExpression")
        TestCase.assertEquals(2, getOrDefaultExt.valueArgumentCount)
        TestCase.assertEquals("a", getOrDefaultExt.valueArguments[0].evaluate())
        TestCase.assertEquals(null, getOrDefaultExt.valueArguments[1].evaluate())
        val getOrDefaultExtResolved = getOrDefaultExt.resolve()
            .orFail("cant resolve from $getOrDefaultExt")
        TestCase.assertEquals("getOrDefault", getOrDefaultExtResolved.name)
        TestCase.assertEquals("CollectionsJDK8Kt", getOrDefaultExtResolved.containingClass?.name)

        val getOrDefault = uFile.findElementByTextFromPsi<UCallExpression>("getOrDefault(\"a\", \"b\")", strict = false)
            .orFail("cant convert to UCallExpression")
        TestCase.assertEquals(2, getOrDefault.valueArgumentCount)
        TestCase.assertEquals("a", getOrDefault.valueArguments[0].evaluate())
        TestCase.assertEquals("b", getOrDefault.valueArguments[1].evaluate())
        val getOrDefaultResolved = getOrDefault.resolve()
            .orFail("cant resolve from $getOrDefault")
        TestCase.assertTrue(getOrDefaultResolved is PsiCompiledElement)
        TestCase.assertEquals("getOrDefault", getOrDefaultResolved.name)
        TestCase.assertEquals("Map", getOrDefaultResolved.containingClass?.name)

        val remove = uFile.findElementByTextFromPsi<UCallExpression>("remove", strict = false)
            .orFail("cant convert to UCallExpression")
        val removeResolved = remove.resolve()
            .orFail("cant resolve from $remove")
        TestCase.assertTrue(removeResolved is PsiCompiledElement)
        TestCase.assertEquals("remove", removeResolved.name)
        TestCase.assertEquals("Map", removeResolved.containingClass?.name)
    }

    fun checkListIterator(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
            fun foo() {
              val li = ArrayList<String>().listIterator()
              li.<caret>add("test")
            }
            """.trimIndent()
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val resolved = uCallExpression.resolve()
            .orFail("cant resolve from $uCallExpression")
        TestCase.assertEquals("add", resolved.name)
        TestCase.assertEquals("ListIterator", resolved.containingClass?.name)
    }

    fun checkStringJVM(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
            fun foo() {
                "with default".capitalize()
                "without default".capitalize(Locale.US)
                "with default".toUpperCase()
                "without default".toUpperCase(Locale.US)
            }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        val withDefaultCapitalize = uFile.findElementByTextFromPsi<UCallExpression>("capitalize()", strict = false)
            .orFail("cant convert to UCallExpression")
        val withDefaultCapitalizeResolved = withDefaultCapitalize.resolve()
            .orFail("cant resolve from $withDefaultCapitalize")
        TestCase.assertEquals("capitalize", withDefaultCapitalizeResolved.name)
        TestCase.assertEquals(1, withDefaultCapitalizeResolved.parameterList.parametersCount)
        TestCase.assertEquals("java.lang.String", withDefaultCapitalizeResolved.parameterList.parameters[0].type.canonicalText)

        val withoutDefaultCapitalize = uFile.findElementByTextFromPsi<UCallExpression>("capitalize(Locale.US)", strict = false)
            .orFail("cant convert to UCallExpression")
        val withoutDefaultCapitalizeResolved = withoutDefaultCapitalize.resolve()
            .orFail("cant resolve from $withoutDefaultCapitalize")
        TestCase.assertEquals("capitalize", withoutDefaultCapitalizeResolved.name)
        TestCase.assertEquals(2, withoutDefaultCapitalizeResolved.parameterList.parametersCount)
        TestCase.assertEquals("java.lang.String", withoutDefaultCapitalizeResolved.parameterList.parameters[0].type.canonicalText)
        TestCase.assertEquals("java.util.Locale", withoutDefaultCapitalizeResolved.parameterList.parameters[1].type.canonicalText)

        val withDefaultUpperCase = uFile.findElementByTextFromPsi<UCallExpression>("toUpperCase()", strict = false)
            .orFail("cant convert to UCallExpression")
        val withDefaultUpperCaseResolved = withDefaultUpperCase.resolve()
            .orFail("cant resolve from $withDefaultUpperCase")
        TestCase.assertEquals("toUpperCase", withDefaultUpperCaseResolved.name)
        TestCase.assertEquals(1, withDefaultUpperCaseResolved.parameterList.parametersCount)
        TestCase.assertEquals("java.lang.String", withDefaultUpperCaseResolved.parameterList.parameters[0].type.canonicalText)

        val withoutDefaultUpperCase = uFile.findElementByTextFromPsi<UCallExpression>("toUpperCase(Locale.US)", strict = false)
            .orFail("cant convert to UCallExpression")
        val withoutDefaultUpperCaseResolved = withoutDefaultUpperCase.resolve()
            .orFail("cant resolve from $withoutDefaultUpperCase")
        TestCase.assertEquals("toUpperCase", withoutDefaultUpperCaseResolved.name)
        TestCase.assertEquals(2, withoutDefaultUpperCaseResolved.parameterList.parametersCount)
        TestCase.assertEquals("java.lang.String", withoutDefaultUpperCaseResolved.parameterList.parameters[0].type.canonicalText)
        TestCase.assertEquals("java.util.Locale", withoutDefaultUpperCaseResolved.parameterList.parameters[1].type.canonicalText)
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
        val valueOfCall = testValueOf.findElementByText<UElement>("valueOf").uastParent as UCallExpression
        val resolvedValueOfCall = valueOfCall.resolve()
        TestCase.assertNotNull("Unresolved MyEnum.valueOf(String)", resolvedValueOfCall)
        TestCase.assertNotNull("Null return type of $resolvedValueOfCall", resolvedValueOfCall?.returnType)
        TestCase.assertTrue(
            "Missing nullness annotations on $resolvedValueOfCall",
            resolvedValueOfCall!!.annotations.any { it.isNullnessAnnotation }
        )

        val testValues = uFile.findElementByTextFromPsi<UMethod>("testValues", strict = false)
        TestCase.assertNotNull("testValues should be successfully converted", testValues)
        val valuesCall = testValues.findElementByText<UElement>("values").uastParent as UCallExpression
        val resolvedValuesCall = valuesCall.resolve()
        TestCase.assertNotNull("Unresolved MyEnum.values()", resolvedValuesCall)
        TestCase.assertNotNull("Null return type of $resolvedValuesCall", resolvedValuesCall?.returnType)
        TestCase.assertTrue(
            "Missing nullness annotations on $resolvedValuesCall",
            resolvedValuesCall!!.annotations.any { it.isNullnessAnnotation }
        )
    }

    fun checkArrayAccessOverloads(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """
            public class SparseArray<E> {
              private Map<Long, E> map = new HashMap<Long, E>();
              public void set(int key, E value) { map.put(key, value); }
              public void set(long key, E value) { map.put(key, value); }
              public E get(int key) { return map.get(key); }
              public E get(long key) { return map.get(key); }
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                fun foo(array: SparseArray<String>) {
                  array[42L] = "forty"
                  val y = array[42]
                  array[42L] += " two"
                }
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElement()!!

        val set = uFile.findElementByTextFromPsi<UArrayAccessExpression>("array[42L]", strict = false)
            .orFail("cant convert to UArrayAccessExpression")
        val setResolved = (set.resolve() as? PsiMethod)
            .orFail("cant resolve from $set")
        TestCase.assertEquals("set", setResolved.name)
        TestCase.assertEquals(2, setResolved.parameterList.parameters.size)
        TestCase.assertEquals("long", setResolved.parameterList.parameters[0].type.canonicalText)
        TestCase.assertEquals("E", setResolved.parameterList.parameters[1].type.canonicalText)
        TestCase.assertEquals("void", setResolved.returnType?.canonicalText)

        val op = uFile.findElementByTextFromPsi<UBinaryExpression>("array[42L] =", strict = false)
            .orFail("cant convert to UBinaryExpression")
        val opResolved = op.resolveOperator()
            .orFail("cant resolve from $op")
        TestCase.assertEquals(setResolved, opResolved)

        val get = uFile.findElementByTextFromPsi<UArrayAccessExpression>("array[42]", strict = false)
            .orFail("cant convert to UArrayAccessExpression")
        val getResolved = (get.resolve() as? PsiMethod)
            .orFail("cant resolve from $get")
        TestCase.assertEquals("get", getResolved.name)
        TestCase.assertEquals(1, getResolved.parameterList.parameters.size)
        TestCase.assertEquals("int", getResolved.parameterList.parameters[0].type.canonicalText)
        TestCase.assertEquals("E", getResolved.returnType?.canonicalText)

        val augmented = uFile.findElementByTextFromPsi<UBinaryExpression>("array[42L] +=", strict = false)
            .orFail("cant convert to UBinaryExpression")
        val augmentedResolved = augmented.resolveOperator()
            .orFail("cant resolve from $augmented")
        // NB: not exactly same as above one, which is `E get(int)`, whereas this one is `E get(long)`
        TestCase.assertEquals(getResolved.name, augmentedResolved.name)
        TestCase.assertEquals(1, augmentedResolved.parameterList.parameters.size)
        TestCase.assertEquals("long", augmentedResolved.parameterList.parameters[0].type.canonicalText)
        TestCase.assertEquals("E", augmentedResolved.returnType?.canonicalText)
    }

    fun checkOperatorOverloads(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                data class Point(val x: Int, val y: Int) {
                  operator fun unaryMinus() = Point(-x, -y)
                  operator fun inc() = Point(x + 1, y + 1)
                  
                  operator fun plus(increment: Int) = Point(x + increment, y + increment)
                  operator fun plus(other: Point) = Point(x + other.x, y + other.y)
                }
                operator fun Point.unaryPlus() = Point(+x, +y)
                operator fun Point.dec() = Point(x - 1, y - 1)
                
                fun foo(u: Point, v: Point) {
                  -u
                  +u
                  --u
                  ++u
                  u--
                  u++
                  u + 1
                  u + v
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        val minusU = uFile.findElementByTextFromPsi<UPrefixExpression>("-u", strict = false)
            .orFail("cant convert to UPrefixExpression")
        TestCase.assertEquals("-", minusU.operatorIdentifier?.name)
        val unaryMinus = minusU.resolveOperator()
        TestCase.assertEquals("unaryMinus", unaryMinus?.name)
        TestCase.assertEquals("Point", unaryMinus?.containingClass?.name)

        val plusU = uFile.findElementByTextFromPsi<UPrefixExpression>("+u", strict = false)
            .orFail("cant convert to UPrefixExpression")
        TestCase.assertEquals("+", plusU.operatorIdentifier?.name)
        val unaryPlus = plusU.resolveOperator()
        TestCase.assertEquals("unaryPlus", unaryPlus?.name)
        TestCase.assertEquals("MainKt", unaryPlus?.containingClass?.name)

        val minusMinusU = uFile.findElementByTextFromPsi<UPrefixExpression>("--u", strict = false)
            .orFail("cant convert to UPrefixExpression")
        TestCase.assertEquals("--", minusMinusU.operatorIdentifier?.name)
        val dec1 = minusMinusU.resolveOperator()
        TestCase.assertEquals("dec", dec1?.name)
        TestCase.assertEquals("MainKt", dec1?.containingClass?.name)

        val plusPlusU = uFile.findElementByTextFromPsi<UPrefixExpression>("++u", strict = false)
            .orFail("cant convert to UPrefixExpression")
        TestCase.assertEquals("++", plusPlusU.operatorIdentifier?.name)
        val inc1 = plusPlusU.resolveOperator()
        TestCase.assertEquals("inc", inc1?.name)
        TestCase.assertEquals("Point", inc1?.containingClass?.name)

        val uMinusMinus = uFile.findElementByTextFromPsi<UPostfixExpression>("u--", strict = false)
            .orFail("cant convert to UPostfixExpression")
        TestCase.assertEquals("--", uMinusMinus.operatorIdentifier?.name)
        val dec2 = uMinusMinus.resolveOperator()
        TestCase.assertEquals("dec", dec2?.name)
        TestCase.assertEquals("MainKt", dec2?.containingClass?.name)

        val uPlusPlus = uFile.findElementByTextFromPsi<UPostfixExpression>("u++", strict = false)
            .orFail("cant convert to UPostfixExpression")
        TestCase.assertEquals("++", uPlusPlus.operatorIdentifier?.name)
        val inc2 = uPlusPlus.resolveOperator()
        TestCase.assertEquals("inc", inc2?.name)
        TestCase.assertEquals("Point", inc2?.containingClass?.name)

        val uPlusOne = uFile.findElementByTextFromPsi<UBinaryExpression>("u + 1", strict = false)
            .orFail("cant convert to UBinaryExpression")
        TestCase.assertEquals("+", uPlusOne.operatorIdentifier?.name)
        val plusOne = uPlusOne.resolveOperator()
        TestCase.assertEquals("plus", plusOne?.name)
        TestCase.assertEquals("increment", plusOne?.parameters?.get(0)?.name)
        TestCase.assertEquals("Point", plusOne?.containingClass?.name)

        val uPlusV = uFile.findElementByTextFromPsi<UBinaryExpression>("u + v", strict = false)
            .orFail("cant convert to UBinaryExpression")
        TestCase.assertEquals("+", uPlusV.operatorIdentifier?.name)
        val plusPoint = uPlusV.resolveOperator()
        TestCase.assertEquals("plus", plusPoint?.name)
        TestCase.assertEquals("other", plusPoint?.parameters?.get(0)?.name)
        TestCase.assertEquals("Point", plusPoint?.containingClass?.name)
    }

    fun checkOperatorMultiResolvable(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """
            public class SparseArray<E> {
              private Map<Long, E> map = new HashMap<Long, E>();
              public void set(int key, E value) { map.put(key, value); }
              public void set(long key, E value) { map.put(key, value); }
              public E get(int key) { return map.get(key); }
              public E get(long key) { return map.get(key); }
              public void setSize(long s) {}
              public long getSize() { return map.size(); }
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                data class Point(val x: Int, val y: Int) {
                  operator fun inc() = Point(x + 1, y + 1)
                }
                operator fun Point.dec() = Point(x - 1, y - 1)

                fun test(array: SparseArray<String>) {
                  var i = Point(0, 0)

                  i++
                  i--

                  ++i
                  --i

                  array[42L] = "forty"
                  array[42L] += " two"
                  
                  array.size += 42
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        val iPlusPlus = uFile.findElementByTextFromPsi<UPostfixExpression>("i++", strict = false)
            .orFail("cant convert to UPostfixExpression")
        val iPlusPlusResolvedDeclarations = (iPlusPlus as UMultiResolvable).multiResolve()
        val iPlusPlusResolvedDeclarationsStrings = iPlusPlusResolvedDeclarations.map { it.element?.text ?: "<null>" }
        assertContainsElements(
            iPlusPlusResolvedDeclarationsStrings,
            "var i = Point(0, 0)",
            "operator fun inc() = Point(x + 1, y + 1)",
        )

        val iMinusMinus = uFile.findElementByTextFromPsi<UPostfixExpression>("i--", strict = false)
            .orFail("cant convert to UPostfixExpression")
        val iMinusMinusResolvedDeclarations = (iMinusMinus as UMultiResolvable).multiResolve()
        val iMinusMinusResolvedDeclarationsStrings = iMinusMinusResolvedDeclarations.map { it.element?.text ?: "<null>" }
        assertContainsElements(
            iMinusMinusResolvedDeclarationsStrings,
            "var i = Point(0, 0)",
            "operator fun Point.dec() = Point(x - 1, y - 1)",
        )

        val plusPlusI = uFile.findElementByTextFromPsi<UPrefixExpression>("++i", strict = false)
            .orFail("cant convert to UPrefixExpression")
        val plusPlusIResolvedDeclarations = (plusPlusI as UMultiResolvable).multiResolve()
        val plusPlusIResolvedDeclarationsStrings = plusPlusIResolvedDeclarations.map { it.element?.text ?: "<null>" }
        assertContainsElements(
            plusPlusIResolvedDeclarationsStrings,
            "var i = Point(0, 0)",
            "operator fun inc() = Point(x + 1, y + 1)",
        )

        val minusMinusI = uFile.findElementByTextFromPsi<UPrefixExpression>("--i", strict = false)
            .orFail("cant convert to UPrefixExpression")
        val minusMinusIResolvedDeclarations = (minusMinusI as UMultiResolvable).multiResolve()
        val minusMinusIResolvedDeclarationsStrings = minusMinusIResolvedDeclarations.map { it.element?.text ?: "<null>" }
        assertContainsElements(
            minusMinusIResolvedDeclarationsStrings,
            "var i = Point(0, 0)",
            "operator fun Point.dec() = Point(x - 1, y - 1)",
        )

        val aEq = uFile.findElementByTextFromPsi<UBinaryExpression>("array[42L] =", strict = false)
            .orFail("cant convert to UBinaryExpression")
        val aEqResolvedDeclarations = (aEq as UMultiResolvable).multiResolve()
        val aEqResolvedDeclarationsStrings = aEqResolvedDeclarations.map { it.element?.text ?: "<null>" }
        assertContainsElements(
            aEqResolvedDeclarationsStrings,
            "public void set(long key, E value) { map.put(key, value); }",
        )

        val aPlusEq = uFile.findElementByTextFromPsi<UBinaryExpression>("array[42L] +=", strict = false)
            .orFail("cant convert to UBinaryExpression")
        val aPlusEqResolvedDeclarations = (aPlusEq as UMultiResolvable).multiResolve()
        val aPlusEqResolvedDeclarationsStrings = aPlusEqResolvedDeclarations.map { it.element?.text ?: "<null>" }
        assertContainsElements(
            aPlusEqResolvedDeclarationsStrings,
            "public E get(long key) { return map.get(key); }",
            "public void set(long key, E value) { map.put(key, value); }",
        )

        val arraySizePlusEq = uFile.findElementByTextFromPsi<UBinaryExpression>("array.size +=", strict = false)
            .orFail("cant convert to UBinaryExpression")
        val arraySizePlusEqResolvedDeclarations = (arraySizePlusEq as UMultiResolvable).multiResolve()
        val arraySizePlusEqResolvedDeclarationsStrings = arraySizePlusEqResolvedDeclarations.map { it.element?.text ?: "<null>" }
        assertContainsElements(
            arraySizePlusEqResolvedDeclarationsStrings,
            "public long getSize() { return map.size(); }",
            "public void setSize(long s) {}",
        )
    }

    fun checkResolveSyntheticJavaPropertyAccessor(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """public class X {
        |String getFoo();
        |void setFoo(String s);
        |}""".trimMargin()
        )

        myFixture.configureByText(
            "main.kt", """
                fun box(x : X) {
                  x.foo = "42"
                }
            """.trimIndent()
        )

        val visitor = PropertyAccessorVisitor { it.endsWith("foo") || it.endsWith("setFoo") }
        myFixture.file.toUElement()!!.accept(visitor)
        TestCase.assertEquals(2, visitor.resolvedElements.size)
        val nodes = visitor.resolvedElements.keys
        TestCase.assertTrue(nodes.any { it is USimpleNameReferenceExpression })
        // Will create on-the-fly accessor call for Java synthetic property
        TestCase.assertTrue(nodes.any { it is UCallExpression})
        // Both simple name reference (`foo`) and its on-the-fly accessor call are resolved to the same Java synthetic property accessor.
        val resolvedPsiElements = visitor.resolvedElements.values.toSet()
        TestCase.assertEquals(1, resolvedPsiElements.size)
        TestCase.assertEquals("setFoo", (resolvedPsiElements.single() as PsiMethod).name)
    }

    fun checkResolveKotlinPropertyAccessor(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "Foo.kt", """
                class X {
                  val foo : String
                    get() = "forty two"
                  
                  fun viaAnonymousInner() {
                    val btn = object : Any() {
                      val x = foo
                    }
                  }
                }
            """.trimIndent()
        )

        val visitor = PropertyAccessorVisitor { it.endsWith("foo") || it.endsWith("getFoo") }
        myFixture.file.toUElement()!!.accept(visitor)
        TestCase.assertEquals(1, visitor.resolvedElements.size)
        val nodes = visitor.resolvedElements.keys
        TestCase.assertTrue(nodes.all { it is USimpleNameReferenceExpression })
        // Should not create on-the-fly accessor call for Kotlin property
        TestCase.assertTrue(nodes.none { it is UCallExpression})
        val resolvedPsiElements = visitor.resolvedElements.values.toSet()
        TestCase.assertEquals(1, resolvedPsiElements.size)
        TestCase.assertEquals("getFoo", (resolvedPsiElements.single() as PsiMethod).name)
    }

    private class PropertyAccessorVisitor(
        private val nameFilter : (String) -> Boolean
    ) : AbstractUastVisitor() {
        val resolvedElements = mutableMapOf<UElement, PsiElement>()

        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
            val name = node.resolvedName ?: return false
            if (!nameFilter.invoke(name)) {
                return false
            }
            node.resolve()?.let { resolvedElements[node] = it }
            return true
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val name = node.methodName ?: return false
            if (!nameFilter.invoke(name)) {
                return false
            }
            node.resolve()?.let { resolvedElements[node] = it }
            return true
        }
    }

    fun checkResolveStaticImportFromObject(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                package test.pkg
                
                import test.pkg.Foo.bar
                
                object Foo {
                    fun bar() {}
                }
                
                fun test() {
                    ba<caret>r()
                }
            """.trimIndent()
        )
        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val bar = uCallExpression.resolve()
            .orFail("cant resolve $uCallExpression")
        TestCase.assertEquals("bar", bar.name)
        TestCase.assertEquals("Foo", bar.containingClass?.name)
    }
    
    fun checkResolveToSubstituteOverride(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                open class Box<T>(
                  open val t: T
                ) {
                  fun foo(): T { return t }
                }
                
                class SubBox(
                  override val t: String
                ) : Box<String>(t)
                
                fun box() {
                  val b = SubBox("hi")
                  b.fo<caret>o()
                }
            """.trimIndent()
        )
        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val foo = uCallExpression.resolve()
            .orFail("cant resolve $uCallExpression")
        // NB: the return type is not a substituted type, String, but the original one, T, since it's resolved to
        // the original function Box#foo()T, not a fake overridden one in SubBox.
        TestCase.assertEquals("T", foo.returnType?.canonicalText)
    }

    fun checkResolveEnumEntrySuperType(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
             package test.pkg
             enum class MyEnum(mode: String) {
                ENUM_ENTRY_1("Mode1") {
                    override fun toString(): String {
                        return super.toString()
                    }
                }
            }               
            """.trimIndent()
        )

        myFixture.file.toUElement()!!.accept(
            object : AbstractUastVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    node.uastSuperTypes.forEach(::visitTypeReferenceExpression)
                    return false
                }

                override fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean {
                    // Without proper parent / containing file chain,
                    // resolve() triggers an exception from [ClsJavaCodeReferenceElementImpl.diagnoseNoFile]
                    val psiClass = (node.type as? PsiClassType)?.resolve()
                    TestCase.assertNotNull(psiClass)
                    // Enum entry ENUM_ENTRY_1 is the only one that has an explicit super type: its containing enum class
                    TestCase.assertEquals("MyEnum", psiClass?.name)
                    return false
                }
            }
        )
    }

    fun checkLambdaInvoke(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                val lambda = {}

                fun box() {
                  lambda()
                  lambda.invoke()

                  val lambda_local = {}
                  lambda_local()
                  lambda_local.invoke()
                }
            """.trimIndent()
        )

        myFixture.file.toUElement()!!.accept(
            object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val resolved = node.resolve()
                    TestCase.assertNotNull(resolved)
                    TestCase.assertEquals("invoke", resolved!!.name)

                    val receiver = node.receiver
                    TestCase.assertNotNull(receiver)
                    val resolvedReceiverName = (node.receiver as? UReferenceExpression)?.resolvedName
                    TestCase.assertNotNull(resolvedReceiverName)
                    TestCase.assertTrue(
                        resolvedReceiverName!!.startsWith("lambda") ||
                                resolvedReceiverName.startsWith("getLambda")
                    )

                    return super.visitCallExpression(node)
                }
            }
        )
    }

}
