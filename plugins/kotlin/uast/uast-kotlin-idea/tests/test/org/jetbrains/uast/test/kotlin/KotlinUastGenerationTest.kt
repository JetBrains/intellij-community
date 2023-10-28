// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.uast.testFramework.env.findElementByTextFromPsi
import com.intellij.platform.uast.testFramework.env.findUElementByTextFromPsi
import com.intellij.psi.PsiTypes
import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.generate.UParameterInfo
import org.jetbrains.uast.generate.replace
import kotlin.test.fail as kfail

class KotlinUastGenerationTest : AbstractKotlinUastGenerationTest() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()


    fun `test method call generation with receiver`() {
      `do test method call generation with receiver`("null", "null")
    }

    fun `test simple reference creating from variable`() {
        val context = dummyContextFile()
        val variable = uastElementFactory.createLocalVariable(
            "a", PsiTypes.intType(), uastElementFactory.createNullLiteral(context), false, context
        )

        val reference = uastElementFactory.createSimpleReference(variable, context) ?: kfail("cannot create reference")
        TestCase.assertEquals("a", reference.identifier)
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
                    UReturnExpression
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
                    UReturnExpression
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
                        UPolyadicExpression (operator = +)
                            ULiteralExpression (value = "10")
            """.trimIndent(), lambda.putIntoVarInitializer().asRecursiveLogString().trim())
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
                    UPolyadicExpression (operator = +)
                        ULiteralExpression (value = "a")
        """.trimIndent(), methodCall.uastParent?.asRecursiveLogString()?.trim()
        )
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
                            UPolyadicExpression (operator = +)
                                ULiteralExpression (value = "abc")
                        UReturnExpression
                            UPolyadicExpression (operator = +)
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
}
