// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.common.kotlin

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.uast.testFramework.env.findElementByText
import com.intellij.platform.uast.testFramework.env.findElementByTextFromPsi
import com.intellij.platform.uast.testFramework.env.findUElementByTextFromPsi
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.replaceService
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.builtins.StandardNames.ENUM_VALUES
import org.jetbrains.kotlin.builtins.StandardNames.ENUM_VALUE_OF
import org.jetbrains.kotlin.idea.base.test.JUnit4Assertions.assertSameElements
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase.assertContainsElements
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase.assertDoesntContain
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.KotlinUFile
import org.jetbrains.uast.kotlin.KotlinUFunctionCallExpression
import org.jetbrains.uast.kotlin.psi.UastFakeLightMethodBase
import org.jetbrains.uast.visitor.AbstractUastVisitor
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

interface UastResolveApiFixtureTestBase {
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

        TestCase.assertEquals(PsiTypes.intType(), functionCall.getExpressionType())

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

        TestCase.assertEquals(PsiTypes.voidType(), functionCall.getExpressionType())
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
        TestCase.assertEquals(PsiTypes.intType(), functionCall.getExpressionType())
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
        TestCase.assertEquals(PsiTypes.intType(), functionCall.getExpressionType())
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
        TestCase.assertEquals(PsiTypes.intType(), functionCall.getExpressionType())
    }

    fun checkLocalResolve_class(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                fun test() {
                  class LocalClass(i: Int)
                  
                  val lc = LocalClass(42)
                  
                  class LocalClassWithImplicitConstructor
                  
                  val lcwic = LocalClassWithImplicitConstructor()
                  
                  class LocalClassWithGeneric<T>(t: T)
                  
                  val lgc = LocalClassWithGeneric<String>("hi")
                }
            """.trimIndent()
        )
        myFixture.file.toUElement()!!.accept(
            object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val resolved = node.resolve()
                    TestCase.assertNotNull(resolved)
                    TestCase.assertTrue(resolved!!.isConstructor)

                    val resolvedViaReference = node.classReference?.resolve()
                    TestCase.assertNotNull(resolvedViaReference)
                    TestCase.assertTrue(resolvedViaReference is PsiClass)
                    TestCase.assertTrue((resolvedViaReference as PsiClass).name?.startsWith("LocalClass") == true)

                    // KTIJ-17870
                    val type = node.getExpressionType()
                    TestCase.assertTrue(type is PsiClassType)
                    TestCase.assertTrue((type as PsiClassType).name.startsWith("LocalClass"))
                    val resolvedFromType = type.resolve()
                    TestCase.assertNotNull(resolvedFromType)
                    TestCase.assertTrue(resolvedFromType is PsiClass)
                    TestCase.assertTrue((resolvedFromType as PsiClass).name?.startsWith("LocalClass") == true)

                    return super.visitCallExpression(node)
                }
            }
        )
    }

    fun checkLocalResolve_function(myFixture: JavaCodeInsightTestFixture) {
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

    fun checkGetJavaClass(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                  fun test() {
                    val x = Test::class.ja<caret>va
                  }
                }
            """
        )
        val j = myFixture.file.findElementAt(myFixture.caretOffset).toUElement()?.getParentOfType<USimpleNameReferenceExpression>()
            .orFail("cant convert to USimpleNameReferenceExpression")
        val resolved = j.resolve() as? PsiMethod
        // With @JvmName("getJavaClass") on getter
        TestCase.assertEquals("getJavaClass", resolved?.name)
        // Java Class, not KClass
        TestCase.assertEquals("java.lang.Class<T>", resolved?.returnType?.canonicalText)
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

    fun checkResolveJavaDefaultConstructor(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """public class JavaClass { }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                fun test() {
                  val instance = Java<caret>Class()
                }
            """.trimIndent()
        )
        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        // KTIJ-21555
        val notResolved = uCallExpression.resolve()
        TestCase.assertNull(notResolved)
        val resolved = uCallExpression.classReference?.resolve() as? PsiClass
        TestCase.assertNotNull(resolved)
        TestCase.assertEquals("JavaClass", resolved!!.name)
    }

    fun checkResolveKotlinDefaultConstructor(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class KotlinClass { }
                fun test() {
                  val instance = Kotlin<caret>Class()
                }
            """.trimIndent()
        )
        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val resolved = uCallExpression.resolve()
            .orFail("cant resolve from $uCallExpression")
        TestCase.assertTrue("Not resolved to Kotlin class default constructor", resolved.isConstructor)
        TestCase.assertEquals("KotlinClass", resolved.name)
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

        // Inspired by https://issuetracker.google.com/issues/298483892
        val uParameter = resolved.toUElementOfType<UParameter>()
        TestCase.assertNotNull(uParameter)
        TestCase.assertEquals(resolved.name, uParameter!!.name)
    }

    fun checkResolveImplicitLambdaParameter_binary(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """
                package test.pkg;
                
                public class Foo {
                }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                package test.pkg

                inline fun <T, R> T.use(block: (T) -> R): R {
                  return block(this)
                }
                
                class Test {
                  lateinit var x: Foo
                    private set
                    
                  init {
                    Foo().use {
                      x = it
                    }
                  }
                }
            """.trimIndent()
        )

        val assign = myFixture.file.findUElementByTextFromPsi<UBinaryExpression>("x = it", strict = false)
            .orFail("cant convert to UBinaryExpression")
        val ref = assign.rightOperand as USimpleNameReferenceExpression
        TestCase.assertEquals("it", ref.identifier)
        // No source for implicit lambda parameter. Expect to be resolved to fake PsiParameter used inside ULambdaExpression
        val resolved = (ref.resolve() as? PsiParameter)
            .orFail("cant resolve implicit lambda parameter")
        TestCase.assertEquals("it", resolved.name)
    }

    fun checkNullityOfResolvedLambdaParameter(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """
                class MyLiveData<T> {
                  public void addSource(MyLiveData<S> source, Observer<? super S> onChanged) {}
                  public void setValue(T value) {}
                }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                fun interface Observer<T> {
                  fun onChanged(value: T)
                }
                
                class Test {
                  val myData = MyLiveData<List<Boolean>>()
                  
                  init {
                    myData.addSource(getSources()) { data ->
                      myData.value = da<caret>ta
                    }
                  }
                  
                  private fun getSources(): MyLiveData<List<Boolean>> = TODO()
                }
            """.trimIndent()
        )
        val ref = myFixture.file.findElementAt(myFixture.caretOffset).toUElement()?.getParentOfType<UReferenceExpression>()
            .orFail("cant convert to UReferenceExpression")
        val resolved = (ref.resolve() as? PsiParameter)
            .orFail("cant resolve lambda parameter")
        TestCase.assertFalse(resolved.annotations.any { it.isNullnessAnnotation })
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

    fun checkArgumentMappingSAM(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                    fun interface Foo {
                        fun foo()
                    }

                    fun uiMethod() {}

                    fun test(foo: Foo) {}

                    fun testLambda() {
                        te<caret>st { uiMethod() }
                    }
                }
            """.trimIndent()
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val resolved = uCallExpression.resolve()
            .orFail("cant resolve from $uCallExpression")

        resolved.parameters.forEachIndexed { index, _ ->
            val arg = uCallExpression.getArgumentForParameter(index)
            TestCase.assertNotNull(arg)
            TestCase.assertTrue(arg is ULambdaExpression)
            TestCase.assertEquals("Test.Foo", (arg as ULambdaExpression).functionalInterfaceType?.canonicalText)
        }
    }

    fun checkArgumentMappingSAM_methodReference(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                    fun interface Foo {
                        fun foo()
                    }

                    fun uiMethod() {}

                    fun test(foo: Foo) {}
                    
                    fun testMethodRef() {
                      te<caret>st(this::uiMethod)
                    }
                }
            """.trimIndent()
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val resolved = uCallExpression.resolve()
            .orFail("cant resolve from $uCallExpression")

        resolved.parameters.forEachIndexed { index, _ ->
            val arg = uCallExpression.getArgumentForParameter(index)
            TestCase.assertNotNull(arg)
            TestCase.assertTrue(arg is UCallableReferenceExpression)
            TestCase.assertEquals("uiMethod", (arg as UCallableReferenceExpression).callableName)
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

    fun checkPrimitiveOperator(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                fun testString(s1: String, s2: String) {
                    return s1 + s2
                }

                fun testLong(l1: Long, l2: Long) {
                    return l1 + l2
                }

                fun testInt(i1: Int, i2: Int) {
                    return i1 + i2
                }

                fun testIntRange(ir1: IntRange, ir2: IntRange) {
                    return ir1 + ir2
                }
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElement()!!

        var count = 0
        uFile.accept(
            object : AbstractUastVisitor() {
                override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                    val resolved = node.resolveOperator()
                    val receiverType = node.leftOperand.getExpressionType()
                    if (receiverType in PsiTypes.primitiveTypes()) {
                        TestCase.assertNull("${resolved?.containingClass?.name}#${resolved?.name}", resolved)
                    } else {
                        TestCase.assertEquals(node.sourcePsi?.text, "plus", resolved?.name)
                    }
                    count++
                    return super.visitBinaryExpression(node)
                }
            }
        )
        TestCase.assertEquals(4, count)
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
                  var x = Point(0, 0)
                  x += Point(4, 2)
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
        var plusPoint = uPlusV.resolveOperator()
        TestCase.assertEquals("plus", plusPoint?.name)
        TestCase.assertEquals("other", plusPoint?.parameters?.get(0)?.name)
        TestCase.assertEquals("Point", plusPoint?.containingClass?.name)

        val xPlusEq = uFile.findElementByTextFromPsi<UBinaryExpression>("x +=", strict = false)
            .orFail("cant convert to UBinaryExpression")
        TestCase.assertEquals("+", uPlusV.operatorIdentifier?.name)
        plusPoint = xPlusEq.resolveOperator()
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

    fun checkResolveSyntheticJavaPropertyCompoundAccess(myFixture: JavaCodeInsightTestFixture, isK2 : Boolean = true) {
        myFixture.addClass(
            """public class X {
        |int getFoo() { return 42; }
        |void setFoo(int s) {}
        |}""".trimMargin()
        )

        myFixture.configureByText(
            "main.kt", """
                fun box(x : X) {
                  x.foo += 42
                  x.foo++
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val plusEq = uFile.findElementByTextFromPsi<UBinaryExpression>("x.foo +=", strict = false)
            .orFail("cant convert to UBinaryExpression")
        val wholePlusEq = plusEq.resolveOperator()
        TestCase.assertNull("${wholePlusEq?.containingClass?.name}#${wholePlusEq?.name}", wholePlusEq)
        // `x.foo` from `x.foo += 42`
        val left = (plusEq.leftOperand as? UResolvable)?.resolve() as? PsiMethod
        if (isK2) {
            TestCase.assertEquals("getFoo", left?.name)
        } else {
            TestCase.assertEquals("setFoo", left?.name)
        }

        val plusEqMulti = (plusEq as UMultiResolvable).multiResolve()
        val plusEqMultiStrings = plusEqMulti.map { it.element?.text ?: "<null>" }
        assertContainsElements(
            plusEqMultiStrings,
            "int getFoo() { return 42; }",
            "void setFoo(int s) {}",
        )

        val plusPlus = uFile.findElementByTextFromPsi<UUnaryExpression>("x.foo++", strict = false)
            .orFail("cant convert to UUnaryExpression")
        val wholePlusPlus = plusPlus.resolveOperator()
        TestCase.assertNull("${wholePlusPlus?.containingClass?.name}#${wholePlusPlus?.name}", wholePlusPlus)
        // `x.foo` from `x.foo++`
        val operand = (plusPlus.operand as? UResolvable)?.resolve() as? PsiMethod
        if (isK2) {
            TestCase.assertEquals("getFoo", operand?.name)
        } else {
            TestCase.assertEquals("setFoo", operand?.name)
        }

        val plusPlusMulti = (plusPlus as UMultiResolvable).multiResolve()
        val plusPlusMultiStrings = plusPlusMulti.map { it.element?.text ?: "<null>" }
        assertContainsElements(
            plusPlusMultiStrings,
            "int getFoo() { return 42; }",
            "void setFoo(int s) {}",
        )
    }

    fun checkResolveSyntheticJavaPropertyAccessor_setter(myFixture: JavaCodeInsightTestFixture) {
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

    fun checkResolveSyntheticJavaPropertyAccessor_getter(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """public class X {
        |String getFoo();
        |void setFoo(String s);
        |}""".trimMargin()
        )

        myFixture.addClass(
            """public interface I {
        |String getFoo();
        |}""".trimMargin()
        )

        myFixture.configureByText(
            "main.kt", """
                class Y : X(), I {
                }
                fun box(y : Y): Boolean {
                  val yo = y.foo ?: return false
                  return yo == "42"
                }
            """.trimIndent()
        )

        val visitor = PropertyAccessorVisitor { it.endsWith("foo") || it.endsWith("getFoo") }
        myFixture.file.toUElement()!!.accept(visitor)
        TestCase.assertEquals(2, visitor.resolvedElements.size)
        val nodes = visitor.resolvedElements.keys
        TestCase.assertTrue(nodes.any { it is USimpleNameReferenceExpression })
        // Will create on-the-fly accessor call for Java synthetic property
        TestCase.assertTrue(nodes.any { it is UCallExpression})
        // Both simple name reference (`foo`) and its on-the-fly accessor call are resolved to the same Java synthetic property accessor.
        val resolvedPsiElements = visitor.resolvedElements.values.toSet()
        TestCase.assertEquals(1, resolvedPsiElements.size)
        TestCase.assertEquals("getFoo", (resolvedPsiElements.single() as PsiMethod).name)
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
            val name = node.resolvedName ?: return super.visitSimpleNameReferenceExpression(node)
            if (!nameFilter.invoke(name)) {
                return super.visitSimpleNameReferenceExpression(node)
            }
            node.resolve()?.let { resolvedElements[node] = it }
            return super.visitSimpleNameReferenceExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val name = node.methodName ?: return super.visitCallExpression(node)
            if (!nameFilter.invoke(name)) {
                return super.visitCallExpression(node)
            }
            node.resolve()?.let { resolvedElements[node] = it }
            return super.visitCallExpression(node)
        }
    }

    fun checkResolveBackingField(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                  var prop: String = "42"
                    get() {
                      return field + "?"
                    }
                    set(value) {
                      field = value + "!"
                    }
                }
            """.trimIndent()
        )

        myFixture.file.toUElement()!!.accept(BackingFieldResolveVisitor)
    }

    fun checkResolveBackingFieldInCompanionObject(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                  companion object {
                    var prop: String = "42"
                      get() {
                        return field + "?"
                      }
                      set(value) {
                        field = value + "!"
                      }
                  }
                }
            """.trimIndent()
        )

        myFixture.file.toUElement()!!.accept(BackingFieldResolveVisitor)
    }

    private object BackingFieldResolveVisitor : AbstractUastVisitor() {
        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
            if (node.identifier != "field")
                return super.visitSimpleNameReferenceExpression(node)

            val resolved = node.resolve()
            TestCase.assertNotNull(resolved)
            TestCase.assertEquals("prop", (resolved as PsiField).name)
            TestCase.assertEquals("Test", resolved.containingClass?.name)

            return super.visitSimpleNameReferenceExpression(node)
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
                    return super.visitClass(node)
                }

                override fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean {
                    // Without proper parent / containing file chain,
                    // resolve() triggers an exception from [ClsJavaCodeReferenceElementImpl.diagnoseNoFile]
                    val psiClass = (node.type as? PsiClassType)?.resolve()
                    TestCase.assertNotNull(psiClass)
                    // Enum entry ENUM_ENTRY_1 is the only one that has an explicit super type: its containing enum class
                    TestCase.assertEquals("MyEnum", psiClass?.name)
                    return super.visitTypeReferenceExpression(node)
                }
            }
        )
    }

    fun checkResolveFunInterfaceSamWithValueClassInSignature(myFixture: JavaCodeInsightTestFixture, isK2: Boolean) {
        // Test inspired from https://issuetracker.google.com/314048176
        myFixture.configureByText(
            "main.kt", """
                @JvmInline
                value class MyValue(val p: Int)

                fun interface FunInterface {
                  fun sam(): MyValue
                }

                fun itfConsumer(itf: FunInterface) {
                  itf.sam().p
                }

                fun test() {
                  itfConsumer {
                    MyValue(42)
                  }
                }
            """.trimIndent()
        )

        myFixture.file.toUElement()!!.accept(
            object : AbstractUastVisitor() {
                private fun PsiModifierListOwner.isAbstract(): Boolean =
                    modifierList?.hasModifierProperty(PsiModifier.ABSTRACT) == true ||
                            hasModifierProperty(PsiModifier.ABSTRACT)

                override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                    val superClass = (node.functionalInterfaceType as? PsiClassReferenceType)?.resolve()
                    TestCase.assertNotNull(superClass)
                    val superMethod = superClass!!.methods.singleOrNull { it.isAbstract() }
                    if (isK2) {
                        // SLC does not model a member with `value` class in its signature
                        TestCase.assertNull(superMethod)
                    } else {
                        TestCase.assertNotNull(superMethod)
                    }

                    val superUClass = superClass.toUElementOfType<UClass>()
                    TestCase.assertNotNull(superUClass)
                    val superUMethod = superUClass!!.methods.singleOrNull { it.isAbstract() }
                    TestCase.assertNotNull(superUMethod)
                    if (isK2) {
                        // In K2 UAST, we'll see a fake [UMethod].
                        TestCase.assertTrue(superUMethod!!.javaPsi is UastFakeLightMethodBase)
                    } else {
                        // In K1 UAST, we'll see a [UMethod] that uses the ULC element.
                        TestCase.assertEquals(superMethod, superUMethod!!.javaPsi)
                    }

                    return super.visitLambdaExpression(node)
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
                    TestCase.assertEquals(OperatorNameConventions.INVOKE.identifier, resolved!!.name)

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

    fun checkIsMethodCallCanBeOneOfInvoke(myFixture: JavaCodeInsightTestFixture) {
        @Language("kotlin")
        val mainCode = """               
                operator fun Int.invoke() {}
                
                fun call() {
                   val foo = 1
                   foo()
                }
            """.trimIndent()
        myFixture.configureByText("main.kt", mainCode)
        myFixture.checkIsMethodNameCanBeOneOf(listOf("invoke"))
    }

    fun checkIsMethodCallCanBeOneOfRegularMethod(myFixture: JavaCodeInsightTestFixture) {
        @Language("kotlin")
        val mainCode = """               
                fun foo(){}
                
                fun call() {
                   foo()
                }
            """.trimIndent()
        myFixture.configureByText("main.kt", mainCode)
        myFixture.checkIsMethodNameCanBeOneOf(listOf("foo"))
    }

    fun checkIsMethodCallCanBeOneOfConstructor(myFixture: JavaCodeInsightTestFixture) {
        @Language("kotlin")
        val mainCode = """               
                class Foo
                
                fun call() {
                   Foo()
                }
            """.trimIndent()
        myFixture.configureByText("main.kt", mainCode)
        myFixture.checkIsMethodNameCanBeOneOf(listOf("<init>"))
    }

    fun checkIsMethodCallCanBeOneOfImportAliased(myFixture: JavaCodeInsightTestFixture) {
        @Language("kotlin")
        val mainCode = """
                import kotlin.collections.listOf as lst
                fun call() {
                   lst(1)
                }
            """.trimIndent()
        myFixture.configureByText("main.kt", mainCode)
        myFixture.checkIsMethodNameCanBeOneOf(listOf("listOf"))
    }

    private fun JavaCodeInsightTestFixture.checkIsMethodNameCanBeOneOf(names: Collection<String>) {
        var call: UCallExpression? = null
        val uFile = file.toUElement() as KotlinUFile
        uFile.accept(
            object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    if (call != null) {
                        error("Only a single call should be present in the test")
                    }
                    call = node
                    return super.visitCallExpression(node)
                }
            }
        )
        check(call != null) {
            "Call should be present in the test"
        }
        TestCase.assertTrue(call is KotlinUFunctionCallExpression)
        val ktCall = call as KotlinUFunctionCallExpression
        TestCase.assertTrue("expected method name to be one of ${names}", ktCall.isMethodNameOneOf(names))
    }

    fun checkParentOfParameterOfCatchClause(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                try {
                } catch (exception : Exception) {
                  println(exception)
                }
            """.trimIndent()
        )

        myFixture.file.toUElement()!!.accept(object : AbstractUastVisitor() {
            private var catchClause: UCatchClause? = null

            override fun visitCatchClause(node: UCatchClause): Boolean {
                catchClause = node
                return super.visitCatchClause(node)
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
                val resolved = node.resolve()?.toUElement()
                TestCase.assertTrue(resolved?.uastParent is UCatchClause)
                TestCase.assertEquals(catchClause, resolved)
                return super.visitSimpleNameReferenceExpression(node)
            }
        })
    }

    fun checkCompanionConstantAsVarargAnnotationValue(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                package test.pkg

                @Retention(AnnotationRetention.SOURCE)
                @Target(AnnotationTarget.ANNOTATION_CLASS)
                annotation class MyIntDef(
                  vararg val value: Int = [],
                  val flag: Boolean = false,
                )

                class RemoteAuthClient internal constructor(
                  private val packageName: String,
                ) {
                  companion object {
                    const val NO_ERROR: Int = -1
                    const val ERROR_UNSUPPORTED: Int = 0
                    const val ERROR_PHONE_UNAVAILABLE: Int = 1

                    @MyIntDef(NO_ERROR, ERROR_UNSUPPORTED, ERROR_PHONE_UNAVAILABLE)
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class ErrorCode
                  }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElementOfType<UFile>()!!
        val remote = uFile.classes.find { it.name == "RemoteAuthClient" }
            .orFail("cant find RemoteAuthClient")
        // RemoteAuthClient -> .Companion -> .ErrorCode
        val errorCode = remote.innerClasses.single().innerClasses.single()
        val metaAnnotation = errorCode.uAnnotations.find { it.qualifiedName?.endsWith("MyIntDef") == true }
            .orFail("cant find @MyIntDef annotation")
        // NO_ERROR, ERROR_UNSUPPORTED, ERROR_PHONE_UNAVAILABLE
        val varargs = metaAnnotation.attributeValues.single().expression as UCallExpression
        for (value in varargs.valueArguments) {
            TestCase.assertTrue(value is USimpleNameReferenceExpression)
            val resolved = (value as USimpleNameReferenceExpression).resolve()
            TestCase.assertEquals(remote.javaPsi, (resolved as PsiField).containingClass)
        }
    }

    fun checkResolveThisExpression(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Foo {
                  fun myMethod() = 42
                  
                  fun test() {
                    this.myMethod()
                  }
                }
                
                fun Foo.ext() = this.myMethod()
                
                val Foo.ext: Int
                    get() = this.myMethod()
            """.trimIndent()
        )

        myFixture.file.toUElement()!!.accept(
            object : AbstractUastVisitor() {
                var currentMethod: String? = null

                override fun visitMethod(node: UMethod): Boolean {
                    currentMethod = node.name

                    return super.visitMethod(node)
                }

                override fun afterVisitMethod(node: UMethod) {
                    currentMethod = null

                    super.afterVisitMethod(node)
                }

                override fun visitThisExpression(node: UThisExpression): Boolean {
                    val resolved = node.resolve()
                    TestCase.assertNotNull(resolved)

                    if (currentMethod == "ext" || currentMethod == "getExt") {
                        TestCase.assertTrue(resolved is PsiParameter)
                        TestCase.assertEquals("\$this\$ext", (resolved as PsiParameter).name)
                        TestCase.assertEquals("Foo", resolved.type.canonicalText)
                    } else {
                        TestCase.assertTrue(resolved is PsiClass)
                        TestCase.assertEquals("Foo", (resolved as PsiClass).name)
                    }

                    return super.visitThisExpression(node)
                }
            }
        )
    }

    fun checkResolveThisExpressionAsLambdaReceiver(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                package some
                
                interface MyCoroutineScope
                
                suspend fun <R> coroutineScope(block: MyCoroutineScope.() -> R): R = TODO()
                
                class Foo {
                  val list = listOf(1)

                  fun myMethod() {}

                  fun consumeScope(scope: MyCoroutineScope) {}

                  suspend fun testClassProperty() = coroutineScope outer@{ // this: MyCoroutineScope
                    list.isEmpty()
                    this@Foo.list.isEmpty()

                    list.apply { // this: List
                      this@apply.isEmpty()
                      this.isEmpty() // same as above, just no label

                      consumeScope(this@outer)
                    }

                    myMethod()
                    this@Foo.myMethod()

                    consumeScope(this@outer)
                    consumeScope(this) // same as above, just no label
                  }
                }
            """.trimIndent()
        )

        val callables = setOf("TODO", "listOf", "coroutineScope", "isEmpty", "apply", "myMethod", "consumeScope")
        myFixture.file.toUElement()!!.accept(
            object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val resolved = node.resolve()
                    TestCase.assertNotNull(resolved)
                    TestCase.assertTrue(resolved!!.name in callables)

                    return super.visitCallExpression(node)
                }

                var lastThis: PsiParameter? = null

                override fun visitThisExpression(node: UThisExpression): Boolean {
                    val resolved = node.resolve()
                    TestCase.assertNotNull(resolved)

                    val text = node.sourcePsi?.text ?: "not this?"
                    if (text.contains("@Foo")) {
                        // this@Foo
                        TestCase.assertTrue(resolved is PsiClass)
                        TestCase.assertEquals("Foo", (resolved as PsiClass).name)
                    } else {
                        // this@apply, this, this@outer
                        TestCase.assertTrue(resolved is PsiParameter)
                        TestCase.assertEquals("<this>", (resolved as PsiParameter).name)

                        when (text) {
                            "this" -> {
                                // `this` is deliberately tested always *after* labeled `this`
                                TestCase.assertEquals(lastThis!!.type.canonicalText, resolved.type.canonicalText)
                            }
                            "this@apply" -> {
                                TestCase.assertEquals(
                                    "java.util.List<? extends java.lang.Integer>",
                                    resolved.type.canonicalText
                                )
                            }
                            "this@outer" -> {
                                TestCase.assertEquals("some.MyCoroutineScope", resolved.type.canonicalText)
                            }
                            else -> error("unexpected UThisExpression: $text")
                        }

                        lastThis = resolved
                    }

                    return super.visitThisExpression(node)
                }
            }
        )
    }

    fun checkResolvePropertiesInCompanionObjectFromBinaryDependency(myFixture: JavaCodeInsightTestFixture) {
        val mockLibraryFacility = myFixture.configureLibraryByText(
            "dependency.kt", """
                package some

                interface Flag<T>

                class Dependency {
                  companion object {
                    @JvmField val JVM_FIELD_FLAG: Flag<*> = TODO()
                    @JvmStatic val JVM_STATIC_FLAG: Flag<*> = TODO()
                    val VAL_FLAG: Flag<*> = TODO()
                    var varFlag: Flag<*> = TODO()
                  }
                }
                
                class OtherDependency {
                  companion object Named {
                      @JvmField val JVM_FIELD_FLAG: Flag<*> = TODO()
                      @JvmStatic val JVM_STATIC_FLAG: Flag<*> = TODO()
                      val VAL_FLAG: Flag<*> = TODO()
                      var varFlag: Flag<*> = TODO()
                  }
                }
                
                object DependencyObject {
                  val VAL_FLAG: Flag<*> = TODO()
                  var varFlag: Flag<*> = TODO()
                }

                val DEPENDENCY_TOP_LEVEL_VAL_FLAG: Flag<*> = TODO()
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                package some

                private fun consumeFlag(p: Flag<*>) {
                  println(p)
                }

                fun test() {
                  consumeFlag(Dependency.JVM_FIELD_FLAG)
                  consumeFlag(Dependency.JVM_STATIC_FLAG)
                  consumeFlag(Dependency.VAL_FLAG)
                  consumeFlag(Dependency.varFlag)
                  consumeFlag(OtherDependency.JVM_FIELD_FLAG)
                  consumeFlag(OtherDependency.JVM_STATIC_FLAG)
                  consumeFlag(OtherDependency.VAL_FLAG)
                  consumeFlag(OtherDependency.varFlag)
                  consumeFlag(DependencyObject.VAL_FLAG)
                  consumeFlag(DependencyObject.varFlag)
                  consumeFlag(DEPENDENCY_TOP_LEVEL_VAL_FLAG)
                }
            """.trimIndent()
        )

        val containingClassQueue = buildList {
            repeat(4) { add("Dependency") }
            repeat(4) { add("OtherDependency") }
            repeat(2) { add("DependencyObject") }
            add("DependencyKt")
        }
        val fieldQueue = buildList {
            repeat(2) {
                add("JVM_FIELD_FLAG")
                add("JVM_STATIC_FLAG")
                add("VAL_FLAG")
                add("varFlag")
            }
            add("VAL_FLAG")
            add("varFlag")
            add("DEPENDENCY_TOP_LEVEL_VAL_FLAG")
        }

        try {
            myFixture.file.toUElement()!!.accept(
                PropertyFromBinaryDependencyVisitor(containingClassQueue, fieldQueue)
            )
        } finally {
            mockLibraryFacility.tearDown(myFixture.module)
        }
    }

    fun checkResolvePropertiesInInnerClassFromBinaryDependency(myFixture: JavaCodeInsightTestFixture) {
        val mockLibraryFacility = myFixture.configureLibraryByText(
            "dependency.kt", """
                package some

                interface Flag<T>

                class Outer {
                  val VAL_FLAG: Flag<*> = TODO()
                  var varFlag: Flag<*> = TODO()

                  inner class Inner {
                    val VAL_FLAG: Flag<*> = TODO()
                    var varFlag: Flag<*> = TODO()
                  }
                  
                  object O {
                    val VAL_FLAG: Flag<*> = TODO()
                    var varFlag: Flag<*> = TODO()
                  }
                }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                package some

                private fun consumeFlag(p: Flag<*>) {
                  println(p)
                }

                fun test() {
                  val o = Outer()
                  consumeFlag(o.VAL_FLAG)
                  consumeFlag(o.varFlag)
                  val i = o.Inner()
                  consumeFlag(i.VAL_FLAG)
                  consumeFlag(i.varFlag)
                  consumeFlag(Outer.O.VAL_FLAG)
                  consumeFlag(Outer.O.varFlag)
                }
            """.trimIndent()
        )

        val containingClassQueue = buildList {
            repeat(2) { add("Outer") }
            repeat(2) { add("Inner") }
            repeat(2) { add("O") }
        }
        val fieldQueue = buildList {
            repeat(3) {
                add("VAL_FLAG")
                add("varFlag")
            }
        }

        try {
            myFixture.file.toUElement()!!.accept(
                PropertyFromBinaryDependencyVisitor(containingClassQueue, fieldQueue)
            )
        } finally {
            mockLibraryFacility.tearDown(myFixture.module)
        }
    }

    private class PropertyFromBinaryDependencyVisitor(
        val containingClassQueue: List<String>,
        val fieldQueue: List<String>
    ) : AbstractUastVisitor() {
        init {
            assert(containingClassQueue.size == fieldQueue.size)
        }

        var count = 0

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val arg = node.valueArguments.singleOrNull()
                ?: return super.visitCallExpression(node)
            if (arg.sourcePsi?.text == "p") {
                // Test call-sites of `consumeFlag`, not `consumeFlag` itself.
                return super.visitCallExpression(node)
            }

            val selector = when (arg) {
                is UQualifiedReferenceExpression -> arg.selector
                is UParenthesizedExpression -> arg.expression
                else -> arg
            } as USimpleNameReferenceExpression
            val resolved = selector.resolve()
            TestCase.assertNotNull(node.asRenderString(), resolved)
            TestCase.assertTrue(resolved is PsiField)

            val fieldName = fieldQueue[count]
            TestCase.assertEquals(fieldName, (resolved as PsiField).name)
            val className = containingClassQueue[count]
            TestCase.assertEquals(className, resolved.containingClass?.name)
            count++

            return super.visitCallExpression(node)
        }

        override fun afterVisitFile(node: UFile) {
            TestCase.assertEquals(containingClassQueue.size, count)

            super.afterVisitFile(node)
        }
    }

    fun checkResolveConstructorCallFromLibrary(myFixture: JavaCodeInsightTestFixture) {
        val mockLibraryFacility = myFixture.configureLibraryByText(
            "Test.kt", """
                package test.pkg

                open class Test(
                    val p: String
                )
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                import test.pkg.Test
                
                object : Test("hi")
            """.trimIndent()
        )

        try {
            val uFile = myFixture.file.toUElementOfType<UFile>()!!
            uFile.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val resolved = node.resolve()
                    TestCase.assertNotNull(resolved)

                    TestCase.assertTrue(resolved!!.isConstructor)
                    val containingClass = resolved.containingClass
                    TestCase.assertEquals("Test", containingClass?.name)

                    return super.visitCallExpression(node)
                }
            })
        } finally {
            mockLibraryFacility.tearDown(myFixture.module)
        }
    }

    fun checkResolveTopLevelInlineReifiedFromLibrary(myFixture: JavaCodeInsightTestFixture, withJvmName: Boolean) {
        val anno = if (withJvmName) "@file:JvmName(\"Mocking\")" else ""
        val mockLibraryFacility = myFixture.configureLibraryByText(
            "Mocking.kt", """
                $anno
                package test

                inline fun <reified T : Any> mock(): T = TODO()

                object Mock {
                  inline fun <reified T : Any> mock(): T = TODO()
                }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                import test.Mock
                import test.mock as tMock

                class MyClass

                fun test(): Boolean {
                  val instance1 = Mock.mock<MyClass>()
                  val instance2 = tMock<MyClass>()
                  return instance1 == instance2
                }
            """.trimIndent()
        )

        try {
            val uFile = myFixture.file.toUElementOfType<UFile>()!!
            uFile.accept(object : AbstractUastVisitor() {
                var first: Boolean = true

                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val resolved = node.resolve()
                    TestCase.assertNotNull(resolved)
                    TestCase.assertEquals("mock", resolved!!.name)
                    if (first) {
                        TestCase.assertEquals("Mock", resolved.containingClass?.name)
                        TestCase.assertFalse(resolved.hasModifier(JvmModifier.STATIC))
                        first = false
                    } else {
                        TestCase.assertEquals(
                            if (withJvmName) "Mocking" else "MockingKt",
                            resolved.containingClass?.name
                        )
                        TestCase.assertTrue(resolved.hasModifier(JvmModifier.STATIC))
                    }
                    TestCase.assertNotNull(resolved.returnType)
                    TestCase.assertEquals("MyClass", resolved.returnType!!.canonicalText)

                    return super.visitCallExpression(node)
                }
            })
        } finally {
            mockLibraryFacility.tearDown(myFixture.module)
        }
    }

    fun checkResolveTopLevelInlineInFacadeFromLibrary(myFixture: JavaCodeInsightTestFixture, isK2: Boolean) {
        val mockLibraryFacility = myFixture.configureLibraryByText(
            "MyStringJVM.kt", """
                @file:kotlin.jvm.JvmMultifileClass
                @file:kotlin.jvm.JvmName("MyStringsKt")
                
                package test.pkg
                
                inline fun belongsToClassPart(): String = TODO()
                
                inline fun <reified T : Any> needFake(): String = TODO()
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                import test.pkg.*
                
                fun test() {
                  belongsToClassPart()
                  needFake()
                }
            """.trimIndent()
        )

        try {
            val uFile = myFixture.file.toUElementOfType<UFile>()!!
            uFile.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val resolved = node.resolve()
                    TestCase.assertNotNull(resolved)

                    val containingClass = resolved!!.containingClass
                    val expectedName =
                        if (isK2) "MyStringsKt" // multi-file facade
                        else "MyStringsKt__MyStringJVMKt" // multi-file class part
                    TestCase.assertEquals(expectedName, containingClass?.name)

                    return super.visitCallExpression(node)
                }
            })
        } finally {
            mockLibraryFacility.tearDown(myFixture.module)
        }
    }

    fun checkResolveInnerInlineFromLibrary(myFixture: JavaCodeInsightTestFixture) {
        val mockLibraryFacility = myFixture.configureLibraryByText(
            "Dependency.kt", """
                package test
                
                class Mock {
                  companion object {
                    inline fun <reified T : Any> mock(): T = TODO()
                  }
                }
                
                class AnotherMock {
                  companion object Named {
                    inline fun <reified T : Any> mock(): T = TODO()
                  }
                }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                import test.Mock
                import test.AnotherMock

                class MyClass

                fun test(): Boolean {
                  val instance1 = Mock.mock<MyClass>()
                  val instance2 = AnotherMock.mock<MyClass>()
                  return instance1 == instance2
                }
            """.trimIndent()
        )

        try {
            val uFile = myFixture.file.toUElementOfType<UFile>()!!
            uFile.accept(object : AbstractUastVisitor() {
                var first: Boolean = true

                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val resolved = node.resolve()
                    TestCase.assertNotNull(resolved)
                    TestCase.assertEquals("mock", resolved!!.name)
                    if (first) {
                        TestCase.assertEquals("Companion", resolved.containingClass?.name)
                        first = false
                    } else {
                        TestCase.assertEquals("Named", resolved.containingClass?.name)
                    }

                    return super.visitCallExpression(node)
                }
            })
        } finally {
            mockLibraryFacility.tearDown(myFixture.module)
        }
    }

    fun checkResolveJvmNameOnFunctionFromLibrary(myFixture: JavaCodeInsightTestFixture) {
        val mockLibraryFacility = myFixture.configureLibraryByText(
            "test/pkg/LibObj.kt", """
                package test.pkg

                object LibObj{
                    @JvmName("notFoo")
                    fun foo() {}
                }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                import test.pkg.LibObj

                fun test() {
                    LibObj.f<caret>oo()
                }
            """.trimIndent()
        )

        try {
            val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
                .orFail("cant convert to UCallExpression")
            val resolved = uCallExpression.resolve()
            TestCase.assertNotNull(resolved)
            TestCase.assertEquals("notFoo", resolved!!.name)
        } finally {
            mockLibraryFacility.tearDown(myFixture.module)
        }
    }

    fun checkResolveJvmNameOnGetterFromLibrary(myFixture: JavaCodeInsightTestFixture) {
        val mockLibraryFacility = myFixture.configureLibraryByText(
            "test/pkg/util.kt", """
                package test.pkg
                
                val Int.prop: Int
                    @JvmName("ownPropGetter")
                    get() = this * 31
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                import test.pkg.*

                fun test() {
                    42.p<caret>rop
                }
            """.trimIndent()
        )

        try {
            val p = myFixture.file.findElementAt(myFixture.caretOffset).toUElement()?.getParentOfType<USimpleNameReferenceExpression>()
                .orFail("cant convert to USimpleNameReferenceExpression")
            val resolved = p.resolve() as? PsiMethod
            TestCase.assertNotNull(resolved)
            TestCase.assertEquals("ownPropGetter", resolved!!.name)
        } finally {
            mockLibraryFacility.tearDown(myFixture.module)
        }
    }

    fun checkResolveJvmNameOnSetterFromLibrary(myFixture: JavaCodeInsightTestFixture) {
        val mockLibraryFacility = myFixture.configureLibraryByText(
            "test/pkg/Test.kt", """
                package test.pkg
                
                class Test(
                    var x: Int
                ) {
                }

                var Test.ext: Int
                    get() = this.x
                    @JvmName("ownPropSetter")
                    set(value) {
                        this.x = value * 31
                    }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                import test.pkg.*

                fun test(t: Test) {
                    t.ex<caret>t = 42
                }
            """.trimIndent()
        )

        try {
            val ext = myFixture.file.findElementAt(myFixture.caretOffset).toUElement()?.getParentOfType<USimpleNameReferenceExpression>()
                .orFail("cant convert to USimpleNameReferenceExpression")
            val resolved = ext.resolve() as? PsiMethod
            TestCase.assertNotNull(resolved)
            TestCase.assertEquals("ownPropSetter", resolved!!.name)
        } finally {
            mockLibraryFacility.tearDown(myFixture.module)
        }
    }

    private fun JavaCodeInsightTestFixture.configureLibraryByText(
        fileName: String,
        text: String,
    ): MockLibraryFacility {
        val path = Path(fileName)
        val file = FileUtil.createTempFile(path.nameWithoutExtension, "." + path.extension)
        file.writeText(text)
        file.deleteOnExit()
        val mockLibraryFacility = MockLibraryFacility(file, attachSources = false)
        mockLibraryFacility.setUp(module)
        return mockLibraryFacility
    }
}
