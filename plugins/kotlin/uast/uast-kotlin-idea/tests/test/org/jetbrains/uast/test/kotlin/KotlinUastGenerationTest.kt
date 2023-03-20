// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.generate.UParameterInfo
import org.jetbrains.uast.generate.UastCodeGenerationPlugin
import org.jetbrains.uast.generate.refreshed
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.kotlin.generate.KotlinUastElementFactory
import org.jetbrains.uast.test.env.findElementByTextFromPsi
import org.jetbrains.uast.test.env.findUElementByTextFromPsi
import org.jetbrains.uast.visitor.UastVisitor
import kotlin.test.fail as kfail

class KotlinUastGenerationTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()


    private val psiFactory
        get() = KtPsiFactory(project)
    private val generatePlugin: UastCodeGenerationPlugin
        get() = UastCodeGenerationPlugin.byLanguage(KotlinLanguage.INSTANCE)!!
    private val uastElementFactory
        get() = generatePlugin.getElementFactory(myFixture.project) as KotlinUastElementFactory

    fun `test logical and operation with simple operands`() {
        val left = psiFactory.createExpression("true").toUElementOfType<UExpression>()
            ?: kfail("Cannot create left UExpression")
        val right = psiFactory.createExpression("false").toUElementOfType<UExpression>()
            ?: kfail("Cannot create right UExpression")

        val expression = uastElementFactory.createBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, dummyContextFile())
            ?: kfail("Cannot create expression")

        TestCase.assertEquals("true && false", expression.sourcePsi?.text)
    }

    fun `test logical and operation with simple operands with parenthesis`() {
        val left = psiFactory.createExpression("(true)").toUElementOfType<UExpression>()
            ?: kfail("Cannot create left UExpression")
        val right = psiFactory.createExpression("(false)").toUElementOfType<UExpression>()
            ?: kfail("Cannot create right UExpression")

        val expression = uastElementFactory.createFlatBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, dummyContextFile())
            ?: kfail("Cannot create expression")

        TestCase.assertEquals("true && false", expression.sourcePsi?.text)
        TestCase.assertEquals("""
            UBinaryExpression (operator = &&)
                ULiteralExpression (value = true)
                ULiteralExpression (value = false)
        """.trimIndent(), expression.putIntoFunctionBody().asRecursiveLogString().trim())
    }

    fun `test logical and operation with simple operands with parenthesis polyadic`() {
        val left = psiFactory.createExpression("(true && false)").toUElementOfType<UExpression>()
            ?: kfail("Cannot create left UExpression")
        val right = psiFactory.createExpression("(false)").toUElementOfType<UExpression>()
            ?: kfail("Cannot create right UExpression")

        val expression = uastElementFactory.createFlatBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, dummyContextFile())
            ?: kfail("Cannot create expression")

        TestCase.assertEquals("true && false && false", expression.sourcePsi?.text)
        TestCase.assertEquals("""
            UBinaryExpression (operator = &&)
                UBinaryExpression (operator = &&)
                    ULiteralExpression (value = true)
                    ULiteralExpression (value = false)
                ULiteralExpression (value = false)
        """.trimIndent(), expression.asRecursiveLogString().trim())
    }

    fun `test simple reference creating from variable`() {
        val context = dummyContextFile()
        val variable = uastElementFactory.createLocalVariable(
            "a", PsiTypes.intType(), uastElementFactory.createNullLiteral(context), false, context
        )

        val reference = uastElementFactory.createSimpleReference(variable, context) ?: kfail("cannot create reference")
        TestCase.assertEquals("a", reference.identifier)
    }

    fun `test simple reference by name`() {
        val reference = uastElementFactory.createSimpleReference("a", dummyContextFile())
        TestCase.assertEquals("a", reference.identifier)
    }

    fun `test parenthesised expression`() {
        val expression = psiFactory.createExpression("a + b").toUElementOfType<UExpression>()
            ?: kfail("cannot create expression")
        val parenthesizedExpression = uastElementFactory.createParenthesizedExpression(expression, dummyContextFile())
            ?: kfail("cannot create parenthesized expression")

        TestCase.assertEquals("(a + b)", parenthesizedExpression.sourcePsi?.text)
    }

    fun `test return expression`() {
        val expression = psiFactory.createExpression("a + b").toUElementOfType<UExpression>()
            ?: kfail("Cannot find plugin")

        val returnExpression = uastElementFactory.createReturnExpression(expression, false, dummyContextFile())
        TestCase.assertEquals("a + b", returnExpression.returnExpression?.asRenderString())
        TestCase.assertEquals("return a + b", returnExpression.sourcePsi?.text)
    }

    fun `test variable declaration without type`() {
        val expression = psiFactory.createExpression("1 + 2").toUElementOfType<UExpression>()
            ?: kfail("cannot create variable declaration")

        val declaration = uastElementFactory.createLocalVariable("a", null, expression, false, dummyContextFile())

        TestCase.assertEquals("var a = 1 + 2", declaration.sourcePsi?.text)
    }

    fun `test variable declaration with type`() {
        val expression = psiFactory.createExpression("b").toUElementOfType<UExpression>()
            ?: kfail("cannot create variable declaration")

        val declaration = uastElementFactory.createLocalVariable("a", PsiTypes.doubleType(), expression, false, dummyContextFile())

        TestCase.assertEquals("var a: kotlin.Double = b", declaration.sourcePsi?.text)
    }

    fun `test final variable declaration`() {
        val expression = psiFactory.createExpression("b").toUElementOfType<UExpression>()
            ?: kfail("cannot create variable declaration")

        val declaration = uastElementFactory.createLocalVariable("a", PsiTypes.doubleType(), expression, true, dummyContextFile())

        TestCase.assertEquals("val a: kotlin.Double = b", declaration.sourcePsi?.text)
    }

    fun `test final variable declaration with unique name`() {
        val expression = psiFactory.createExpression("b").toUElementOfType<UExpression>()
            ?: kfail("cannot create variable declaration")

        val declaration = uastElementFactory.createLocalVariable("a", PsiTypes.doubleType(), expression, true, dummyContextFile())

        TestCase.assertEquals("val a: kotlin.Double = b", declaration.sourcePsi?.text)
        TestCase.assertEquals("""
            ULocalVariable (name = a)
                USimpleNameReferenceExpression (identifier = b)
        """.trimIndent(), declaration.asRecursiveLogString().trim())
    }

    fun `test block expression`() {
        val statement1 = psiFactory.createExpression("System.out.println()").toUElementOfType<UExpression>()
            ?: kfail("cannot create statement")
        val statement2 = psiFactory.createExpression("System.out.println(2)").toUElementOfType<UExpression>()
            ?: kfail("cannot create statement")

        val block = uastElementFactory.createBlockExpression(listOf(statement1, statement2), dummyContextFile())

        TestCase.assertEquals("""
                {
                System.out.println()
                System.out.println(2)
                }
               """.trimIndent(), block.sourcePsi?.text
        )
    }

    fun `test lambda expression`() {
        val statement = psiFactory.createExpression("System.out.println()").toUElementOfType<UExpression>()
            ?: kfail("cannot create statement")

        val lambda = uastElementFactory.createLambdaExpression(
            listOf(
                UParameterInfo(PsiTypes.intType(), "a"),
                UParameterInfo(null, "b")
            ),
            statement,
            dummyContextFile()
        ) ?: kfail("cannot create lambda")

        TestCase.assertEquals("{ a: kotlin.Int, b -> System.out.println() }", lambda.sourcePsi?.text)
        TestCase.assertEquals("""
            ULambdaExpression
                UParameter (name = a)
                    UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                UParameter (name = b)
                    UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                UBlockExpression
                    UQualifiedReferenceExpression
                        UQualifiedReferenceExpression
                            USimpleNameReferenceExpression (identifier = System)
                            USimpleNameReferenceExpression (identifier = out)
                        UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0))
                            UIdentifier (Identifier (println))
                            USimpleNameReferenceExpression (identifier = println, resolvesTo = null)
                """.trimIndent(), lambda.putIntoFunctionBody().asRecursiveLogString().trim())
    }

    private fun UExpression.putIntoFunctionBody(): UExpression {
        val file = myFixture.configureByText("dummyFile.kt", "fun foo() { TODO() }") as KtFile
        val ktFunction = file.declarations.single { it.name == "foo" } as KtFunction
        val uMethod = ktFunction.toUElementOfType<UMethod>()!!
        return runWriteCommand {
            uMethod.uastBody.cast<UBlockExpression>().expressions.single().replace(this)!!
        }
    }

    private fun <T : UExpression> T.putIntoVarInitializer(): T {
        val file = myFixture.configureByText("dummyFile.kt", "val foo = TODO()") as KtFile
        val ktFunction = file.declarations.single { it.name == "foo" } as KtProperty
        val uMethod = ktFunction.toUElementOfType<UVariable>()!!
        return runWriteCommand {
            @Suppress("UNCHECKED_CAST")
            generatePlugin.replace(uMethod.uastInitializer!!, this, UExpression::class.java) as T
        }
    }

    private fun <T : UExpression> runWriteCommand(uExpression: () -> T): T {
        val result = Ref<T>()
        WriteCommandAction.runWriteCommandAction(project) {
            result.set(uExpression())
        }
        return result.get()
    }

    fun `test lambda expression with explicit types`() {
        val statement = psiFactory.createExpression("System.out.println()").toUElementOfType<UExpression>()
            ?: kfail("cannot create statement")

        val lambda = uastElementFactory.createLambdaExpression(
            listOf(
                UParameterInfo(PsiTypes.intType(), "a"),
                UParameterInfo(PsiTypes.doubleType(), "b")
            ),
            statement,
            dummyContextFile()
        ) ?: kfail("cannot create lambda")

        TestCase.assertEquals("{ a: kotlin.Int, b: kotlin.Double -> System.out.println() }", lambda.sourcePsi?.text)
        TestCase.assertEquals("""
            ULambdaExpression
                UParameter (name = a)
                    UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                UParameter (name = b)
                    UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                UBlockExpression
                    UQualifiedReferenceExpression
                        UQualifiedReferenceExpression
                            USimpleNameReferenceExpression (identifier = System)
                            USimpleNameReferenceExpression (identifier = out)
                        UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0))
                            UIdentifier (Identifier (println))
                            USimpleNameReferenceExpression (identifier = println, resolvesTo = null)
        """.trimIndent(), lambda.putIntoFunctionBody().asRecursiveLogString().trim())
    }

    fun `test lambda expression with simplified block body with context`() {
        val r = psiFactory.createExpression("return \"10\"").toUElementOfType<UExpression>()
            ?: kfail("cannot create return")

        val block = uastElementFactory.createBlockExpression(listOf(r), dummyContextFile())

        val lambda = uastElementFactory.createLambdaExpression(listOf(UParameterInfo(null, "a")), block, dummyContextFile())
            ?: kfail("cannot create lambda")
        TestCase.assertEquals("""{ a -> "10" }""".trimMargin(), lambda.sourcePsi?.text)
        TestCase.assertEquals("""
            ULambdaExpression
                UParameter (name = a)
                    UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                UBlockExpression
                    UReturnExpression
                        ULiteralExpression (value = "10")
            """.trimIndent(), lambda.putIntoVarInitializer().asRecursiveLogString().trim())
    }

    fun `test function argument replacement`() {

        val file = myFixture.configureByText(
            "test.kt", """
            fun f(a: Any){}

            fun main(){
                f(a)
            }
        """.trimIndent()
        )

        val expression = file.findUElementByTextFromPsi<UCallExpression>("f(a)")
        val newArgument = psiFactory.createExpression("b").toUElementOfType<USimpleNameReferenceExpression>()
            ?: kfail("cannot create reference")

        WriteCommandAction.runWriteCommandAction(project) {
            TestCase.assertNotNull(expression.valueArguments[0].replace(newArgument))
        }

        val updated = expression.refreshed() ?: kfail("cannot update expression")
        TestCase.assertEquals("f", updated.methodName)
        TestCase.assertTrue(updated.valueArguments[0] is USimpleNameReferenceExpression)
        TestCase.assertEquals("b", (updated.valueArguments[0] as USimpleNameReferenceExpression).identifier)
        TestCase.assertEquals("""
            UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                UIdentifier (Identifier (f))
                USimpleNameReferenceExpression (identifier = f, resolvesTo = null)
                USimpleNameReferenceExpression (identifier = b)
        """.trimIndent(), updated.asRecursiveLogString().trim())
    }

    fun `test suggested name`() {
        val expression = psiFactory.createExpression("f(a) + 1").toUElementOfType<UExpression>()
            ?: kfail("cannot create expression")
        val variable = uastElementFactory.createLocalVariable(null, PsiTypes.intType(), expression, true, dummyContextFile())

        TestCase.assertEquals("val i: kotlin.Int = f(a) + 1", variable.sourcePsi?.text)
        TestCase.assertEquals("""
            ULocalVariable (name = i)
                UBinaryExpression (operator = +)
                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                        UIdentifier (Identifier (f))
                        USimpleNameReferenceExpression (identifier = <anonymous class>, resolvesTo = null)
                        USimpleNameReferenceExpression (identifier = a)
                    ULiteralExpression (value = 1)
        """.trimIndent(), variable.asRecursiveLogString().trim())
    }

    fun `test method call generation with receiver`() {
        val receiver = myFixture.configureByKotlinExpression("receiver.kt", "\"10\"")
        val arg1 = myFixture.configureByKotlinExpression("arg1.kt", "1")
        val arg2 = myFixture.configureByKotlinExpression("arg2.kt", "2")
        val methodCall = uastElementFactory.createCallExpression(
            receiver,
            "substring",
            listOf(arg1, arg2),
            null,
            UastCallKind.METHOD_CALL
        ) ?: kfail("cannot create call")

        TestCase.assertEquals(""""10".substring(1,2)""", methodCall.uastParent?.sourcePsi?.text)
        TestCase.assertEquals("""
            UQualifiedReferenceExpression
                ULiteralExpression (value = "10")
                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 2))
                    UIdentifier (Identifier (substring))
                    USimpleNameReferenceExpression (identifier = <anonymous class>, resolvesTo = null)
                    ULiteralExpression (value = null)
                    ULiteralExpression (value = null)
        """.trimIndent(), methodCall.uastParent?.asRecursiveLogString()?.trim()
        )
    }

    fun `test method call generation without receiver`() {
        val arg1 = myFixture.configureByKotlinExpression("arg1.kt", "1")
        val arg2 = myFixture.configureByKotlinExpression("arg2.kt", "2")
        val methodCall = uastElementFactory.createCallExpression(
            null,
            "substring",
            listOf(arg1, arg2),
            null,
            UastCallKind.METHOD_CALL
        ) ?: kfail("cannot create call")

        TestCase.assertEquals("""substring(1,2)""", methodCall.sourcePsi?.text)
    }

    fun `test method call generation with generics restoring`() {
        val arrays = psiFactory.createExpression("java.util.Arrays").toUElementOfType<UExpression>()
            ?: kfail("cannot create receiver")
        val methodCall = uastElementFactory.createCallExpression(
            arrays,
            "asList",
            listOf(),
            createTypeFromText("java.util.List<java.lang.String>", null),
            UastCallKind.METHOD_CALL,
            dummyContextFile()
        ) ?: kfail("cannot create call")
        TestCase.assertEquals("java.util.Arrays.asList<kotlin.String>()", methodCall.uastParent?.sourcePsi?.text)
    }

    fun `test method call generation with generics restoring 2 parameters`() {
        val collections = psiFactory.createExpression("java.util.Collections").toUElementOfType<UExpression>()
            ?: kfail("cannot create receiver")
        TestCase.assertEquals("java.util.Collections", collections.asRenderString())
        val methodCall = uastElementFactory.createCallExpression(
            collections,
            "emptyMap",
            listOf(),
            createTypeFromText(
                "java.util.Map<java.lang.String, java.lang.Integer>",
                null
            ),
            UastCallKind.METHOD_CALL,
            dummyContextFile()
        ) ?: kfail("cannot create call")
        TestCase.assertEquals("emptyMap<kotlin.String,kotlin.Int>()", methodCall.sourcePsi?.text)
        TestCase.assertEquals("java.util.Collections.emptyMap<kotlin.String,kotlin.Int>()", methodCall.sourcePsi?.parent?.text)
        TestCase.assertEquals(
            """
            UQualifiedReferenceExpression
                UQualifiedReferenceExpression
                    UQualifiedReferenceExpression
                        USimpleNameReferenceExpression (identifier = java)
                        USimpleNameReferenceExpression (identifier = util)
                    USimpleNameReferenceExpression (identifier = Collections)
                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0))
                    UIdentifier (Identifier (emptyMap))
                    USimpleNameReferenceExpression (identifier = emptyMap, resolvesTo = null)
                    """.trimIndent(), methodCall.uastParent?.asRecursiveLogString()?.trim()
        )
    }

    private fun dummyContextFile(): KtFile = myFixture.configureByText("file.kt", "fun foo() {}") as KtFile

    fun `test method call generation with generics restoring 1 parameter with 1 existing`() {
        val receiver = myFixture.configureByKotlinExpression("receiver.kt", "A")
        val arg = myFixture.configureByKotlinExpression("arg.kt", "\"a\"")
        val methodCall = uastElementFactory.createCallExpression(
            receiver,
            "kek",
            listOf(arg),
            createTypeFromText(
                "java.util.Map<java.lang.String, java.lang.Integer>",
                null
            ),
            UastCallKind.METHOD_CALL,
            dummyContextFile()
        ) ?: kfail("cannot create call")

        TestCase.assertEquals("A.kek<kotlin.String,kotlin.Int>(\"a\")", methodCall.sourcePsi?.parent?.text)
        TestCase.assertEquals(
            """
            UQualifiedReferenceExpression
                USimpleNameReferenceExpression (identifier = A)
                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                    UIdentifier (Identifier (kek))
                    USimpleNameReferenceExpression (identifier = <anonymous class>, resolvesTo = null)
                    ULiteralExpression (value = "a")
        """.trimIndent(), methodCall.uastParent?.asRecursiveLogString()?.trim()
        )
    }

    fun `test callable reference generation with receiver`() {
        val receiver = uastElementFactory.createQualifiedReference("java.util.Arrays", myFixture.file)
            ?: kfail("failed to create receiver")
        val methodReference = uastElementFactory.createCallableReferenceExpression(receiver, "asList", myFixture.file)
            ?: kfail("failed to create method reference")
        TestCase.assertEquals(methodReference.sourcePsi?.text, "java.util.Arrays::asList")
    }

    fun `test callable reference generation without receiver`() {
        val methodReference = uastElementFactory.createCallableReferenceExpression(null, "asList", myFixture.file)
            ?: kfail("failed to create method reference")
        TestCase.assertEquals(methodReference.sourcePsi?.text, "::asList")
    }

    //not implemented (currently we dont perform resolve in code generating)
    fun `ignore method call generation with generics restoring 1 parameter with 1 unused `() {
        val aClassFile = myFixture.configureByText(
            "A.kt",
            """
                object A {
                    fun <T1, T2, T3> kek(a: T1): Map<T1, T3> {
                        return TODO();
                    }
                }
            """.trimIndent()
        )
        val a = psiFactory.createExpression("A").toUElementOfType<UExpression>()
            ?: kfail("cannot create a receiver")
        val param = psiFactory.createExpression("\"a\"").toUElementOfType<UExpression>()
            ?: kfail("cannot create a parameter")
        val methodCall = uastElementFactory.createCallExpression(
            a,
            "kek",
            listOf(param),
            createTypeFromText(
                "java.util.Map<java.lang.String, java.lang.Integer>",
                null
            ),
            UastCallKind.METHOD_CALL,
            aClassFile
        ) ?: kfail("cannot create call")

        TestCase.assertEquals("A.<String, Object, Integer>kek(\"a\")", methodCall.sourcePsi?.text)
    }


    fun `test method call generation with generics with context`() {
        val file = myFixture.configureByText("file.kt", """
            class A {
                fun <T> method(): List<T> { TODO() }
            }

            fun main(){
               val a = A()
               println(a)
            }
        """.trimIndent()
        ) as KtFile

        val reference = file.findUElementByTextFromPsi<UElement>("println(a)", strict = true)
            .findElementByTextFromPsi<UReferenceExpression>("a")

        val callExpression = uastElementFactory.createCallExpression(
            reference,
            "method",
            emptyList(),
            createTypeFromText(
                "java.util.List<java.lang.Integer>",
                null
            ),
            UastCallKind.METHOD_CALL,
            context = reference.sourcePsi
        ) ?: kfail("cannot create method call")

        TestCase.assertEquals("a.method<kotlin.Int>()", callExpression.uastParent?.sourcePsi?.text)
        TestCase.assertEquals("""
        UQualifiedReferenceExpression
            USimpleNameReferenceExpression (identifier = a)
            UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0))
                UIdentifier (Identifier (method))
                USimpleNameReferenceExpression (identifier = method, resolvesTo = null)
        """.trimIndent(), callExpression.uastParent?.asRecursiveLogString()?.trim()
        )
    }

    fun `test method call generation without generics with context`() {
        val file = myFixture.configureByText("file.kt", """
            class A {
                fun <T> method(t: T): List<T> { TODO() }
            }

            fun main(){
               val a = A()
               println(a)
            }
        """.trimIndent()
        ) as KtFile

        val reference = file.findUElementByTextFromPsi<UElement>("println(a)")
            .findElementByTextFromPsi<UReferenceExpression>("a")

        val callExpression = uastElementFactory.createCallExpression(
            reference,
            "method",
            listOf(uastElementFactory.createIntLiteral(1, file)),
            createTypeFromText(
                "java.util.List<java.lang.Integer>",
                null
            ),
            UastCallKind.METHOD_CALL,
            context = reference.sourcePsi
        ) ?: kfail("cannot create method call")

        TestCase.assertEquals("a.method(1)", callExpression.uastParent?.sourcePsi?.text)
        TestCase.assertEquals("""
            UQualifiedReferenceExpression
                USimpleNameReferenceExpression (identifier = a)
                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                    UIdentifier (Identifier (method))
                    USimpleNameReferenceExpression (identifier = method, resolvesTo = null)
                    ULiteralExpression (value = 1)
        """.trimIndent(), callExpression.uastParent?.asRecursiveLogString()?.trim()
        )
    }


    fun `test replace lambda implicit return value`() {

        val file = myFixture.configureByText(
            "file.kt", """
            fun main(){
                val a: (Int) -> String = {
                    println(it)
                    println(2)
                    "abc"
                }
            }
        """.trimIndent()
        ) as KtFile

        val uLambdaExpression = file.findUElementByTextFromPsi<UInjectionHost>("\"abc\"")
            .getParentOfType<ULambdaExpression>() ?: kfail("cant get lambda")

        val expressions = uLambdaExpression.body.cast<UBlockExpression>().expressions
        UsefulTestCase.assertSize(3, expressions)

        val uReturnExpression = expressions.last() as UReturnExpression
        val newStringLiteral = uastElementFactory.createStringLiteralExpression("def", file)

        val defReturn = runWriteCommand { uReturnExpression.replace(newStringLiteral) ?: kfail("cant replace") }
        val uLambdaExpression2 = defReturn.getParentOfType<ULambdaExpression>() ?: kfail("cant get lambda")

        TestCase.assertEquals("{\n        println(it)\n        println(2)\n        \"def\"\n    }", uLambdaExpression2.sourcePsi?.text)
        TestCase.assertEquals(
            """
        ULambdaExpression
            UParameter (name = it)
            UBlockExpression
                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                    UIdentifier (Identifier (println))
                    USimpleNameReferenceExpression (identifier = println, resolvesTo = null)
                    USimpleNameReferenceExpression (identifier = it)
                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                    UIdentifier (Identifier (println))
                    USimpleNameReferenceExpression (identifier = println, resolvesTo = null)
                    ULiteralExpression (value = 2)
                UReturnExpression
                    ULiteralExpression (value = "def")
        """.trimIndent(), uLambdaExpression2.asRecursiveLogString().trim()
        )

    }


    private class UserDataChecker {

        private val storedData = Any()
        private val KEY = Key.create<Any>("testKey")

        private lateinit var uniqueStringLiteralText: String

        fun store(uElement: UInjectionHost) {
            val psiElement = uElement.sourcePsi as KtStringTemplateExpression
            uniqueStringLiteralText = psiElement.text
            psiElement.putCopyableUserData(KEY, storedData)
        }

        fun checkUserDataAlive(uElement: UElement) {
            val psiElements = uElement.let { SyntaxTraverser.psiTraverser(it.sourcePsi) }
                .filter(KtStringTemplateExpression::class.java)
                .filter { it.text == uniqueStringLiteralText }.toList()

            UsefulTestCase.assertNotEmpty(psiElements)
            UsefulTestCase.assertTrue("uElement still should keep the userdata", psiElements.any { storedData === it!!.getCopyableUserData(KEY) })
        }

    }

    fun `test add intermediate returns to lambda`() {

        val file = myFixture.configureByText(
            "file.kt", """
            fun main(){
                val a: (Int) -> String = lname@{
                    println(it)
                    println(2)
                    "abc"
                }
            }
        """.trimIndent()
        ) as KtFile

        val aliveChecker = UserDataChecker()
        val uLambdaExpression = file.findUElementByTextFromPsi<UInjectionHost>("\"abc\"")
            .also(aliveChecker::store)
            .getParentOfType<ULambdaExpression>() ?: kfail("cant get lambda")

        val oldBlockExpression = uLambdaExpression.body.cast<UBlockExpression>()
        UsefulTestCase.assertSize(3, oldBlockExpression.expressions)

        val conditionalExit = with(uastElementFactory) {
            createIfExpression(
                createBinaryExpression(
                    createSimpleReference("it", uLambdaExpression.sourcePsi),
                    createIntLiteral(3, uLambdaExpression.sourcePsi),
                    UastBinaryOperator.GREATER,
                    uLambdaExpression.sourcePsi
                )!!,
                createReturnExpression(
                    createStringLiteralExpression("exit", uLambdaExpression.sourcePsi), true,
                    uLambdaExpression.sourcePsi
                ),
                null,
                uLambdaExpression.sourcePsi
            )!!
        }

        val newBlockExpression = uastElementFactory.createBlockExpression(
            listOf(conditionalExit) + oldBlockExpression.expressions,
            uLambdaExpression.sourcePsi
        )

        aliveChecker.checkUserDataAlive(newBlockExpression)

        val uLambdaExpression2 = runWriteCommand {
            oldBlockExpression.replace(newBlockExpression) ?: kfail("cant replace")
        }.getParentOfType<ULambdaExpression>() ?: kfail("cant get lambda")

        aliveChecker.checkUserDataAlive(uLambdaExpression2)

        TestCase.assertEquals(
            """
            lname@{
                    if (it > 3) return@lname "exit"
                    println(it)
                    println(2)
                    "abc"
                }
        """.trimIndent(), uLambdaExpression2.sourcePsi?.parent?.text
        )
        TestCase.assertEquals(
            """
        ULambdaExpression
            UParameter (name = it)
            UBlockExpression
                UIfExpression
                    UBinaryExpression (operator = >)
                        USimpleNameReferenceExpression (identifier = it)
                        ULiteralExpression (value = 3)
                    UReturnExpression
                        ULiteralExpression (value = "exit")
                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                    UIdentifier (Identifier (println))
                    USimpleNameReferenceExpression (identifier = println, resolvesTo = null)
                    USimpleNameReferenceExpression (identifier = it)
                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                    UIdentifier (Identifier (println))
                    USimpleNameReferenceExpression (identifier = println, resolvesTo = null)
                    ULiteralExpression (value = 2)
                UReturnExpression
                    ULiteralExpression (value = "abc")
        """.trimIndent(), uLambdaExpression2.asRecursiveLogString().trim()
        )

    }

    fun `test converting lambda to if`() {

        val file = myFixture.configureByText(
            "file.kt", """
            fun foo(call: (Int) -> String): String = call.invoke(2)

            fun main() {
                foo {
                        println(it)
                        println(2)
                        "abc"
                    }
                }
            }
        """.trimIndent()
        ) as KtFile

        val aliveChecker = UserDataChecker()
        val uLambdaExpression = file.findUElementByTextFromPsi<UInjectionHost>("\"abc\"")
            .also { aliveChecker.store(it) }
            .getParentOfType<ULambdaExpression>() ?: kfail("cant get lambda")

        val oldBlockExpression = uLambdaExpression.body.cast<UBlockExpression>()
        aliveChecker.checkUserDataAlive(oldBlockExpression)

        val newLambda = with(uastElementFactory) {
            createLambdaExpression(
                listOf(UParameterInfo(null, "it")),
                createIfExpression(
                    createBinaryExpression(
                        createSimpleReference("it", uLambdaExpression.sourcePsi),
                        createIntLiteral(3, uLambdaExpression.sourcePsi),
                        UastBinaryOperator.GREATER,
                        uLambdaExpression.sourcePsi
                    )!!,
                    oldBlockExpression,
                    createReturnExpression(
                        createStringLiteralExpression("exit", uLambdaExpression.sourcePsi), true,
                        uLambdaExpression.sourcePsi
                    ),
                    uLambdaExpression.sourcePsi
                )!!.also {
                    aliveChecker.checkUserDataAlive(it)
                },
                uLambdaExpression.sourcePsi
            )!!
        }
        aliveChecker.checkUserDataAlive(newLambda)

        val uLambdaExpression2 = runWriteCommand {
            uLambdaExpression.replace(newLambda) ?: kfail("cant replace")
        }

        TestCase.assertEquals(
            """
            { it ->
                    if (it > 3) {
                        println(it)
                        println(2)
                        "abc"
                    } else return@foo "exit"
                }
        """.trimIndent(), uLambdaExpression2.sourcePsi?.parent?.text
        )
        TestCase.assertEquals(
            """
        ULambdaExpression
            UParameter (name = it)
                UAnnotation (fqName = org.jetbrains.annotations.NotNull)
            UBlockExpression
                UReturnExpression
                    UIfExpression
                        UBinaryExpression (operator = >)
                            USimpleNameReferenceExpression (identifier = it)
                            ULiteralExpression (value = 3)
                        UBlockExpression
                            UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                                UIdentifier (Identifier (println))
                                USimpleNameReferenceExpression (identifier = println, resolvesTo = null)
                                USimpleNameReferenceExpression (identifier = it)
                            UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                                UIdentifier (Identifier (println))
                                USimpleNameReferenceExpression (identifier = println, resolvesTo = null)
                                ULiteralExpression (value = 2)
                            ULiteralExpression (value = "abc")
                        UReturnExpression
                            ULiteralExpression (value = "exit")
        """.trimIndent(), uLambdaExpression2.asRecursiveLogString().trim()
        )
        aliveChecker.checkUserDataAlive(uLambdaExpression2)

    }

    fun `test removing unnecessary type parameters while replace`() {
        val aClassFile = myFixture.configureByText(
            "A.kt",
            """
                class A {
                    fun <T> method():List<T> = TODO()
                }
            """.trimIndent()
        )

        val reference = psiFactory.createExpression("a")
            .toUElementOfType<UReferenceExpression>() ?: kfail("cannot create reference expression")
        val callExpression = uastElementFactory.createCallExpression(
            reference,
            "method",
            emptyList(),
            createTypeFromText(
                "java.util.List<java.lang.Integer>",
                null
            ),
            UastCallKind.METHOD_CALL,
            context = aClassFile
        ) ?: kfail("cannot create method call")

        val listAssigment = myFixture.addFileToProject("temp.kt", """
            fun foo(kek: List<Int>) {
                val list: List<Int> = kek
            }
        """.trimIndent()).findUElementByTextFromPsi<UVariable>("val list: List<Int> = kek")

        WriteCommandAction.runWriteCommandAction(project) {
            val methodCall = listAssigment.uastInitializer?.replace(callExpression) ?: kfail("cannot replace!")
            // originally result expected be `a.method()` but we expect to clean up type arguments in other plase
            TestCase.assertEquals("a.method<Int>()", methodCall.sourcePsi?.parent?.text)
        }

    }

    fun `test create if`() {
        val condition = psiFactory.createExpression("true").toUElementOfType<UExpression>()
            ?: kfail("cannot create condition")
        val thenBranch = psiFactory.createBlock("{a(b);}").toUElementOfType<UExpression>()
            ?: kfail("cannot create then branch")
        val elseBranch = psiFactory.createExpression("c++").toUElementOfType<UExpression>()
            ?: kfail("cannot create else branch")
        val ifExpression = uastElementFactory.createIfExpression(condition, thenBranch, elseBranch, dummyContextFile())
            ?: kfail("cannot create if expression")
        TestCase.assertEquals("if (true) {\n        { a(b); }\n    } else c++", ifExpression.sourcePsi?.text)
    }

    fun `test qualified reference`() {
        val reference = uastElementFactory.createQualifiedReference("java.util.List", myFixture.file)
        TestCase.assertEquals("java.util.List", reference?.sourcePsi?.text)
    }

    fun `test build lambda from returning a variable`() {
        val context = dummyContextFile()
        val localVariable = uastElementFactory.createLocalVariable("a", null, uastElementFactory.createNullLiteral(context), true, context)
        val declarationExpression =
            uastElementFactory.createDeclarationExpression(listOf(localVariable), context)
        val returnExpression = uastElementFactory.createReturnExpression(
            uastElementFactory.createSimpleReference(localVariable, context), false, context
        )
        val block = uastElementFactory.createBlockExpression(listOf(declarationExpression, returnExpression), context)

        TestCase.assertEquals("""
            UBlockExpression
                UDeclarationsExpression
                    ULocalVariable (name = a)
                        ULiteralExpression (value = null)
                UReturnExpression
                    USimpleNameReferenceExpression (identifier = a)
        """.trimIndent(), block.asRecursiveLogString().trim())


        val lambda = uastElementFactory.createLambdaExpression(listOf(), block, context) ?: kfail("cannot create lambda expression")
        TestCase.assertEquals("{ val a = null\na }", lambda.sourcePsi?.text)

        TestCase.assertEquals("""
            ULambdaExpression
                UBlockExpression
                    UDeclarationsExpression
                        ULocalVariable (name = a)
                            ULiteralExpression (value = null)
                    UReturnExpression
                        USimpleNameReferenceExpression (identifier = a)
        """.trimIndent(), lambda.putIntoVarInitializer().asRecursiveLogString().trim())
    }

    fun `test expand oneline lambda`() {

        val context = dummyContextFile()
        val parameters = listOf(UParameterInfo(PsiTypes.intType(), "a"))
        val oneLineLambda = with(uastElementFactory) {
            createLambdaExpression(
                parameters,
                createBinaryExpression(
                    createSimpleReference("a", context),
                    createSimpleReference("a", context),
                    UastBinaryOperator.PLUS, context
                )!!, context
            )!!
        }.putIntoVarInitializer()

        val lambdaReturn = (oneLineLambda.body as UBlockExpression).expressions.single()

        val lambda = with(uastElementFactory) {
            createLambdaExpression(
                parameters,
                createBlockExpression(
                    listOf(
                        createCallExpression(
                          null,
                          "println",
                          listOf(createSimpleReference("a", context)),
                          PsiTypes.voidType(),
                          UastCallKind.METHOD_CALL,
                          context
                        )!!,
                        lambdaReturn
                    ),
                    context
                ), context
            )!!
        }

        TestCase.assertEquals("{ a: kotlin.Int -> println(a)\na + a }", lambda.sourcePsi?.text)

        TestCase.assertEquals("""
            ULambdaExpression
                UParameter (name = a)
                    UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                UBlockExpression
                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                        UIdentifier (Identifier (println))
                        USimpleNameReferenceExpression (identifier = println, resolvesTo = null)
                        USimpleNameReferenceExpression (identifier = a)
                    UReturnExpression
                        UBinaryExpression (operator = +)
                            USimpleNameReferenceExpression (identifier = a)
                            USimpleNameReferenceExpression (identifier = a)
        """.trimIndent(), lambda.putIntoVarInitializer().asRecursiveLogString().trim())
    }

    fun `test moving lambda from parenthesis`() {
        myFixture.configureByText("myFile.kt", """
            fun a(p: (Int) -> Unit) {}
        """.trimIndent())


        val lambdaExpression = uastElementFactory.createLambdaExpression(
            emptyList(),
            uastElementFactory.createNullLiteral(null),
            null
        ) ?: kfail("Cannot create lambda")

        val callExpression = uastElementFactory.createCallExpression(
            null,
            "a",
            listOf(lambdaExpression),
            null,
            UastCallKind.METHOD_CALL,
            myFixture.file
        ) ?: kfail("Cannot create method call")

        TestCase.assertEquals("""a{ null }""", callExpression.sourcePsi?.text)
    }

    fun `test saving space after receiver`() {
        myFixture.configureByText("myFile.kt", """
            fun f() {
                a
                    .b()
                    .c<caret>()
                    .d()
            }
        """.trimIndent())

        val receiver =
            myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<KtDotQualifiedExpression>().toUElementOfType<UExpression>()
                ?: kfail("Cannot find UExpression")

        val callExpression = uastElementFactory.createCallExpression(
            receiver,
            "e",
            listOf(),
            null,
            UastCallKind.METHOD_CALL,
            null
        ) ?: kfail("Cannot create call expression")

        TestCase.assertEquals(
            """
                a
                        .b()
                        .c()
                        .e()
            """.trimIndent(),
            callExpression.sourcePsi?.parentOfType<KtDotQualifiedExpression>()?.text
        )
    }

    fun `test initialize field`() {
        val psiFile = myFixture.configureByText("MyClass.kt", """
            class My<caret>Class {
                var field: String?
                fun method(value: String) {
                }
            }
        """.trimIndent())

        val uClass =
          myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>().toUElementOfType<UClass>()
          ?: kfail("Cannot find UClass")
        val uField = uClass.fields.firstOrNull() ?: kfail("Cannot find field")
        val uParameter = uClass.methods.find { it.name == "method"}?.uastParameters?.firstOrNull() ?: kfail("Cannot find parameter")

        WriteCommandAction.runWriteCommandAction(project) { generatePlugin.initializeField(uField, uParameter) }
        TestCase.assertEquals("""
            class MyClass {
                var field: String?
                fun method(value: String) {
                    field = value
                }
            }
        """.trimIndent(), psiFile.text)
    }

    fun `test initialize field with same name`() {
        val psiFile = myFixture.configureByText("MyClass.kt", """
            class My<caret>Class {
                var field: String?
                fun method(field: String) {
                }
            }
        """.trimIndent())

        val uClass =
          myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>().toUElementOfType<UClass>()
          ?: kfail("Cannot find UClass")
        val uField = uClass.fields.firstOrNull() ?: kfail("Cannot find field")
        val uParameter = uClass.methods.find { it.name == "method"}?.uastParameters?.firstOrNull() ?: kfail("Cannot find parameter")

        WriteCommandAction.runWriteCommandAction(project) { generatePlugin.initializeField(uField, uParameter) }
        TestCase.assertEquals("""
            class MyClass {
                var field: String?
                fun method(field: String) {
                    this.field = field
                }
            }
        """.trimIndent(), psiFile.text)
    }

    fun `test initialize field in constructor`() {
        val psiFile = myFixture.configureByText("MyClass.kt", """
            class My<caret>Class() {
                constructor(value: String): this() {
                }
                var field: String?
            }
        """.trimIndent())

        val uClass =
          myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>().toUElementOfType<UClass>()
          ?: kfail("Cannot find UClass")
        val uField = uClass.fields.firstOrNull() ?: kfail("Cannot find field")
        val uParameter = uClass.methods.find { it.isConstructor && it.uastParameters.isNotEmpty() }?.uastParameters?.firstOrNull()
                         ?: kfail("Cannot find parameter")

        initializeField(uField, uParameter)
        TestCase.assertEquals("""
            class MyClass() {
                constructor(value: String): this() {
                    field = value
                }
                var field: String?
            }
        """.trimIndent(), psiFile.text)
    }

    fun `test initialize field in primary constructor`() {
        val psiFile = myFixture.configureByText("MyClass.kt", """
            class My<caret>Class(value: String) {
                val field: String
            }
        """.trimIndent())

        val uClass =
          myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>().toUElementOfType<UClass>()
          ?: kfail("Cannot find UClass")
        val uField = uClass.fields.firstOrNull() ?: kfail("Cannot find field")
        val uParameter = uClass.methods.find { it.uastParameters.isNotEmpty() }?.uastParameters?.firstOrNull() ?: kfail("Cannot find parameter")

        initializeField(uField, uParameter)
        TestCase.assertEquals("""
            class MyClass(value: String) {
                val field: String = value
            }
        """.trimIndent(), psiFile.text)
    }

    fun `test initialize field in primary constructor with same name`() {
        val psiFile = myFixture.configureByText("MyClass.kt", """
            class My<caret>Class(field: String) {
                private val field: String
            }
        """.trimIndent())

        val uClass =
          myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>().toUElementOfType<UClass>()
          ?: kfail("Cannot find UClass")
        val uField = uClass.fields.firstOrNull() ?: kfail("Cannot find field")
        val uParameter = uClass.methods.find { it.uastParameters.isNotEmpty() }?.uastParameters?.firstOrNull() ?: kfail("Cannot find parameter")

        initializeField(uField, uParameter)
        TestCase.assertEquals("""
            class MyClass(private val field: String) {
            }
        """.trimIndent(), psiFile.text)
    }

    fun `test initialize field in primary constructor with same name and class body`() {
        val psiFile = myFixture.configureByText("MyClass.kt", """
            class My<caret>Class(field: String) {
                private val field: String

                public fun test() {
                    val i = 0
                }
            }
        """.trimIndent())

        val uClass =
            myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>().toUElementOfType<UClass>()
                ?: kfail("Cannot find UClass")
        val uField = uClass.fields.firstOrNull() ?: kfail("Cannot find field")
        val uParameter = uClass.methods.find { it.uastParameters.isNotEmpty() }?.uastParameters?.firstOrNull() ?: kfail("Cannot find parameter")

        initializeField(uField, uParameter)
        TestCase.assertEquals("""
            class MyClass(private val field: String) {
                public fun test() {
                    val i = 0
                }
            }
        """.trimIndent(), psiFile.text)
    }

    fun `test initialize field in primary constructor with leading blank line`() {
        val psiFile = myFixture.configureByText("MyClass.kt", """
            class My<caret>Class(field: String) {

                private val field: String

                public fun test() {
                    val i = 0
                }
            }
        """.trimIndent())

        val uClass =
            myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>().toUElementOfType<UClass>()
                ?: kfail("Cannot find UClass")
        val uField = uClass.fields.firstOrNull() ?: kfail("Cannot find field")
        val uParameter = uClass.methods.find { it.uastParameters.isNotEmpty() }?.uastParameters?.firstOrNull() ?: kfail("Cannot find parameter")

        initializeField(uField, uParameter)
        TestCase.assertEquals("""
            class MyClass(private val field: String) {

                public fun test() {
                    val i = 0
                }
            }
        """.trimIndent(), psiFile.text)
    }

    private fun createTypeFromText(s: String, newClass: PsiElement?): PsiType {
        return JavaPsiFacade.getElementFactory(myFixture.project).createTypeFromText(s, newClass)
    }

    private fun JavaCodeInsightTestFixture.configureByKotlinExpression(fileName: String, text: String): UExpression {
        val file = configureByText(fileName, "private val x = $text") as KtFile
        val property = file.declarations.singleOrNull() as? KtProperty ?: error("Property 'x' is not found in $file")
        val initializer = property.initializer ?: error("Property initializer not found in $file")
        return initializer.toUElementOfType() ?: error("Initializer '$initializer' is not convertable to UAST")
    }

    private fun initializeField(uField: UField, uParameter: UParameter) {
        WriteCommandAction.runWriteCommandAction(project) {
            val expression = generatePlugin.initializeField(uField, uParameter)
            assertNotNull(expression)
        }
    }
}

// it is a copy of org.jetbrains.uast.UastUtils.asRecursiveLogString with `appendLine` instead of `appendln` to avoid windows related issues
private fun UElement.asRecursiveLogString(render: (UElement) -> String = { it.asLogString() }): String {
    val stringBuilder = StringBuilder()
    val indent = "    "

    accept(object : UastVisitor {
        private var level = 0

        override fun visitElement(node: UElement): Boolean {
            stringBuilder.append(indent.repeat(level))
            stringBuilder.appendLine(render(node))
            level++
            return false
        }

        override fun afterVisitElement(node: UElement) {
            super.afterVisitElement(node)
            level--
        }
    })
    return stringBuilder.toString()
}
