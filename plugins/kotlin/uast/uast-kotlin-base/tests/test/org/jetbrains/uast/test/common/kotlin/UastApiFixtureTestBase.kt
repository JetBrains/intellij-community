// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.common.kotlin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.uast.testFramework.env.findElementByTextFromPsi
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiTypes
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import junit.framework.TestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.visitor.AbstractUastVisitor

interface UastApiFixtureTestBase {

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

    fun checkArgumentForParameter_smartcast(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                open class A
                class B : A()

                private fun processB(b: B): Int = 2

                fun test(a: A) {
                    if (a is B) {
                        process<caret>B(a)
                    }
                }
            """.trimIndent()
        )

        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        val arg = uCallExpression.getArgumentForParameter(0)
        TestCase.assertNotNull(arg)
        TestCase.assertTrue(arg is USimpleNameReferenceExpression)
        TestCase.assertEquals("a", (arg as? USimpleNameReferenceExpression)?.resolvedName)
    }

    fun checkCallableReferenceWithGeneric(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
               class NonGenericClass
               val reference1 = NonGenericClass::equals

               class GenericClass<T>
               val reference2 = GenericClass<String>::equals 
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        var count = 0
        val expectedQualifierTypes = listOf(
            "NonGenericClass",
            "GenericClass<java.lang.String>",
        )
        val expectedQualifiedExpressionKind = listOf(
            USimpleNameReferenceExpression::class,
            UCallExpression::class, // Type<TypeArgument> is parsed as KtCallElement
        )
        uFile.accept(
            object : AbstractUastVisitor() {
                override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
                    TestCase.assertEquals(expectedQualifierTypes[count], node.qualifierType?.canonicalText)
                    TestCase.assertTrue(expectedQualifiedExpressionKind[count].isInstance(node.qualifierExpression))
                    count++
                    return super.visitCallableReferenceExpression(node)
                }
            }
        )
        TestCase.assertEquals(2, count)
    }

    fun checkCallableReferenceWithGeneric_convertedToSAM(myFixture: JavaCodeInsightTestFixture, isK2: Boolean) {
        myFixture.configureByText(
            "main.kt", """
                import java.lang.Runnable
                import java.util.function.Supplier

                class GenericClass<T> { fun foo(): T = TODO() }
                val runnable1 = Runnable(GenericClass<Any>()::foo)
                val runnable2 = Runnable(GenericClass<String>()::foo)
                val supplier1 = Supplier<Any>(GenericClass<Any>()::foo)
                val supplier2 = Supplier<Any>(GenericClass<String>()::foo)
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        var count = 0
        val expectedQualifierTypes = listOf(
            "GenericClass<java.lang.Object>",
            "GenericClass<java.lang.String>",
            "GenericClass<java.lang.Object>",
            "GenericClass<java.lang.String>",
        )
        // In K2, for SAM conversion to Runnable, method references are resolved as
        // () -> Unit and then mapped to Function0<? extends Unit>.
        val func = if (isK2) "kotlin.jvm.functions.Function0" else "kotlin.reflect.KFunction"
        val expectedExpressionTypes = listOf(
            "$func<? extends kotlin.Unit>",
            "$func<? extends kotlin.Unit>",
            "kotlin.reflect.KFunction<? extends java.lang.Object>",
            "kotlin.reflect.KFunction<? extends java.lang.String>",
        )
        uFile.accept(
            object : AbstractUastVisitor() {
                override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
                    TestCase.assertEquals(expectedQualifierTypes[count], node.qualifierType?.canonicalText)
                    TestCase.assertEquals(expectedExpressionTypes[count], node.getExpressionType()?.canonicalText)
                    count++
                    return super.visitCallableReferenceExpression(node)
                }
            }
        )
        TestCase.assertEquals(4, count)
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
                
                typealias NullableString = String?

                @Deprecated(level = DeprecationLevel.HIDDEN, message="no longer supported")
                fun before(
                    i : Int?,
                    s : String?,
                    ns : NullableString,
                    vararg vs : Any,
                ): State<String> {
                    return object : State<String> {
                        override val value: String = i?.toString() ?: s ?: "42"
                    }
                }
                
                fun after(
                    i : Int?,
                    s : String?,
                    ns : NullableString,
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

        compareDeprecatedHidden(before, after, Nullable::class.java.name)
    }

    fun checkTypesOfDeprecatedHiddenSuspend(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                interface MyInterface

                interface GattClientScope {
                    @Deprecated(level = DeprecationLevel.HIDDEN, message="no longer supported")
                    suspend fun awaitBefore(block: () -> Unit)
                    suspend fun awaitAfter(block: () -> Unit)

                    @Deprecated(level = DeprecationLevel.HIDDEN, message="no longer supported")
                    suspend fun readCharacteristicBefore(p: MyInterface): Result<ByteArray>
                    suspend fun readCharacteristicAfter(p: MyInterface): Result<ByteArray>
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        val awaitBefore = uFile.findElementByTextFromPsi<UMethod>("awaitBefore", strict = false)
            .orFail("cant convert to UMethod: awaitBefore")
        val awaitAfter = uFile.findElementByTextFromPsi<UMethod>("awaitAfter", strict = false)
            .orFail("cant convert to UMethod: awaitAfter")

        compareDeprecatedHidden(awaitBefore, awaitAfter, NotNull::class.java.name)

        val readBefore = uFile.findElementByTextFromPsi<UMethod>("readCharacteristicBefore", strict = false)
            .orFail("cant convert to UMethod: readCharacteristicBefore")
        val readAfter = uFile.findElementByTextFromPsi<UMethod>("readCharacteristicAfter", strict = false)
            .orFail("cant convert to UMethod: readCharacteristicAfter")

        compareDeprecatedHidden(readBefore, readAfter, NotNull::class.java.name)
    }

    fun checkTypesOfDeprecatedHiddenProperty_noAccessor(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                    @Deprecated(level = DeprecationLevel.HIDDEN, "no more property")
                    var pOld_noAccessor: String = "42"
                    var pNew_noAccessor: String = "42"
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val test = uFile.findElementByTextFromPsi<UClass>("Test", strict = false)
        compareDeprecatedHiddenProperty(test, NotNull::class.java.name)
    }

    fun checkTypesOfDeprecatedHiddenProperty_getter(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                    @Deprecated(level = DeprecationLevel.HIDDEN, "no more property")
                    var pOld_getter: String? = null
                        get() = field ?: "null?"
                    var pNew_getter: String? = null
                        get() = field ?: "null?"
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val test = uFile.findElementByTextFromPsi<UClass>("Test", strict = false)
        compareDeprecatedHiddenProperty(test, Nullable::class.java.name)
    }

    fun checkTypesOfDeprecatedHiddenProperty_setter(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                    @Deprecated(level = DeprecationLevel.HIDDEN, "no more property")
                    var pOld_setter: String? = null
                        set(value) {
                            if (field == null) {
                                field = value
                            }
                        }
                    var pNew_setter: String? = null
                        set(value) {
                            if (field == null) {
                                field = value
                            }
                        }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val test = uFile.findElementByTextFromPsi<UClass>("Test", strict = false)
        compareDeprecatedHiddenProperty(test, Nullable::class.java.name)
    }

    fun checkTypesOfDeprecatedHiddenProperty_accessors(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                    @Deprecated(level = DeprecationLevel.HIDDEN, "no more property")
                    var pOld_accessors: String? = null
                        get() = field ?: "null?"
                        set(value) {
                            if (field == null) {
                                field = value
                            }
                        }
                    var pNew_accessors: String? = null
                        get() = field ?: "null?"
                        set(value) {
                            if (field == null) {
                                field = value
                            }
                        }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val test = uFile.findElementByTextFromPsi<UClass>("Test", strict = false)
        compareDeprecatedHiddenProperty(test, Nullable::class.java.name)
    }

    private fun compareDeprecatedHiddenProperty(test: UClass, nullness: String) {
        val old_getter = test.methods.find { it.name.startsWith("getPOld") }
            .orFail("cant find old getter")
        val old_setter = test.methods.find { it.name.startsWith("setPOld") }
            .orFail("cant find old setter")

        val new_getter = test.methods.find { it.name.startsWith("getPNew") }
            .orFail("cant find new getter")
        val new_setter = test.methods.find { it.name.startsWith("setPNew") }
            .orFail("cant find new setter")

        compareDeprecatedHidden(old_getter, new_getter, nullness)
        compareDeprecatedHidden(old_setter, new_setter, nullness)
    }

    private fun compareDeprecatedHidden(before: UMethod, after: UMethod, nullness: String) {
        TestCase.assertEquals("return type", after.returnType, before.returnType)

        TestCase.assertEquals("param size", after.uastParameters.size, before.uastParameters.size)
        after.uastParameters.zip(before.uastParameters).forEach { (afterParam, beforeParam) ->
            val paramName = afterParam.name
            TestCase.assertEquals(paramName, beforeParam.name)
            TestCase.assertEquals(paramName, afterParam.isVarArgs, beforeParam.isVarArgs)
            TestCase.assertEquals(paramName, afterParam.type, beforeParam.type)
            TestCase.assertEquals(
                paramName,
                (afterParam.javaPsi as PsiModifierListOwner).hasAnnotation(nullness),
                (beforeParam.javaPsi as PsiModifierListOwner).hasAnnotation(nullness)
            )
        }
    }

    fun checkReifiedTypeNullability(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                import kotlin.reflect.KClass

                interface NavArgs
                class Fragment
                class Bundle
                class NavArgsLazy<Args : NavArgs>(
                    private val navArgsClass: KClass<Args>,
                    private val argumentProducer: () -> Bundle
                )
                
                inline fun <reified Args : NavArgs> Fragment.navArgs() = NavArgsLazy(Args::class) {
                    throw IllegalStateException("Fragment $this has null arguments")
                }
                
                inline fun <reified Args : NavArgs> Fragment.navArgsNullable(flag: Boolean) =
                    if (flag)
                        NavArgsLazy(Args::class) {
                            throw IllegalStateException("Fragment $this has null arguments")
                        }
                    else
                        null
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        uFile.accept(object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
                if (!node.name.startsWith("navArgs")) return super.visitMethod(node)

                TestCase.assertEquals("NavArgsLazy<Args>", node.javaPsi.returnType?.canonicalText)

                val annotations = node.javaPsi.annotations
                TestCase.assertEquals(1, annotations.size)
                val annotation = annotations.single()
                if (node.name.endsWith("Nullable")) {
                    TestCase.assertTrue(annotation.isNullable)
                } else {
                    TestCase.assertTrue(annotation.isNotNull)
                }

                return super.visitMethod(node)
            }
        })
    }

    fun checkReifiedTypeNullability_generic(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                inline fun <reified T> inlineReified(t: T): T { return t }
                inline fun <reified T> T.inlineReifiedExtension(t: T): T { return this }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        uFile.accept(object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
                val annotations = node.javaPsi.annotations
                TestCase.assertTrue(annotations.isEmpty())
                return super.visitMethod(node)
            }

            override fun visitParameter(node: UParameter): Boolean {
                val annotations = (node.javaPsi as? PsiParameter)?.annotations
                TestCase.assertTrue(annotations?.isEmpty() == true)
                return super.visitParameter(node)
            }
        })
    }

    fun checkInheritedGenericTypeNullability(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class NonNullUpperBound<T : Any>(ctorParam: T) {
                    fun inheritedNullability(i: T): T = i
                    fun explicitNullable(e: T?): T? = e
                }

                class NullableUpperBound<T : Any?>(ctorParam: T) {
                    fun inheritedNullability(i: T): T = i
                    fun explicitNullable(e: T?): T? = e
                }

                class UnspecifiedUpperBound<T>(ctorParam: T) {
                    fun inheritedNullability(i: T): T = i
                    fun explicitNullable(e: T?): T? = e
                }

                fun <T : Any> topLevelNonNullUpperBoundInherited(t: T) = t
                fun <T : Any> T.extensionNonNullUpperBoundInherited(t: T) { }
                fun <T : Any> topLevelNonNullUpperBoundExplicitNullable(t: T?) = t

                fun <T : Any?> topLevelNullableUpperBoundInherited(t: T) = t
                fun <T : Any?> T.extensionNullableUpperBoundInherited(t: T) { }
                fun <T : Any?> topLevelNullableUpperBoundExplicitNullable(t: T?) = t

                fun <T> topLevelUnspecifiedUpperBoundInherited(t: T) = t
                fun <T> T.extensionUnspecifiedUpperBoundInherited(t: T) { }
                fun <T> topLevelUnspecifiedUpperBoundExplicitNullable(t: T?) = t
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val service = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)

        uFile.accept(object : AbstractUastVisitor() {
            var currentMethod: UMethod? = null

            override fun visitMethod(node: UMethod): Boolean {
                if (node.isConstructor) {
                    return super.visitMethod(node)
                }
                currentMethod = node
                return super.visitMethod(node)
            }

            override fun afterVisitMethod(node: UMethod) {
                currentMethod = null
            }

            override fun visitParameter(node: UParameter): Boolean {
                if (currentMethod == null) {
                    return super.visitParameter(node)
                }

                val name = currentMethod!!.name
                val annotations = node.uAnnotations
                if (name.endsWith("Nullable")) {
                    // explicitNullable or ...ExplicitNullable
                    checkNullableAnnotation(annotations)
                } else if (name == "inheritedNullability") {
                    val className = (currentMethod!!.uastParent as UClass).name!!
                    if (className.startsWith("NonNull")) {
                        // non-null upper bound (T: Any)
                        checkNonNullAnnotation(annotations)
                    } else {
                        TestCase.assertTrue(annotations.isEmpty())
                        TestCase.assertTrue(service.hasInheritedGenericType(node.sourcePsi!!))
                    }
                } else {
                    // ...Inherited
                    if (name.contains("NonNull")) {
                        // non-null upper bound (T: Any)
                        checkNonNullAnnotation(annotations)
                    } else {
                        TestCase.assertTrue(annotations.isEmpty())
                        TestCase.assertTrue(service.hasInheritedGenericType(node.sourcePsi!!))
                    }
                }

                return super.visitParameter(node)
            }

            private fun checkNonNullAnnotation(annotations: List<UAnnotation>) {
                TestCase.assertEquals(1, annotations.size)
                val annotation = annotations.single()
                TestCase.assertTrue(annotation.isNotNull)
            }

            private fun checkNullableAnnotation(annotations: List<UAnnotation>) {
                TestCase.assertEquals(1, annotations.size)
                val annotation = annotations.single()
                TestCase.assertTrue(annotation.isNullable)
            }
        })
    }

    fun checkInheritedGenericTypeNullability_propertyAndAccessor(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class CircularArray<E> {
                    val first: E
                        get() = TODO()

                    var last: E
                        get() = TODO()
                        set(value) = TODO()
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val service = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)

        uFile.accept(object : AbstractUastVisitor() {
            override fun visitField(node: UField): Boolean {
                TestCase.assertTrue(node.uAnnotations.isEmpty())
                TestCase.assertTrue(service.hasInheritedGenericType(node.sourcePsi!!))
                return super.visitField(node)
            }

            override fun visitMethod(node: UMethod): Boolean {
                if (node.isConstructor) {
                    return super.visitMethod(node)
                }
                TestCase.assertTrue(node.uAnnotations.isEmpty())
                TestCase.assertTrue(
                    node.returnType == PsiTypes.voidType() || service.hasInheritedGenericType(node.sourcePsi!!)
                )
                return super.visitMethod(node)
            }

            override fun visitParameter(node: UParameter): Boolean {
                TestCase.assertTrue(node.uAnnotations.isEmpty())
                TestCase.assertTrue(service.hasInheritedGenericType(node.sourcePsi!!))
                return super.visitParameter(node)
            }
        })
    }

    fun checkGenericTypeNullability_reified(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                inline fun <reified T> inlineReified(t: T): T { return t }
                inline fun <reified T> T.inlineReifiedExtension(t: T) { this }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val service = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)

        uFile.accept(object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
                TestCase.assertTrue(node.uAnnotations.isEmpty())
                TestCase.assertTrue(
                    node.returnType == PsiTypes.voidType() || service.hasInheritedGenericType(node.sourcePsi!!)
                )
                return super.visitMethod(node)
            }

            override fun visitParameter(node: UParameter): Boolean {
                TestCase.assertTrue(node.uAnnotations.isEmpty())
                TestCase.assertTrue(service.hasInheritedGenericType(node.sourcePsi!!))
                return super.visitParameter(node)
            }
        })
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

    fun checkJavaStaticMethodReceiverType(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """
                public class Controller {
                }
            """.trimIndent()
        )
        myFixture.addClass(
            """
                public class MyService {
                    public static Controller getController() {
                        return new Controller();
                    }
                }
            """.trimIndent()
        )
        myFixture.configureByText(
            "test.kt", """
                fun test() {
                  val controller = MyService.get<caret>Controller()
                }
            """.trimIndent()
        )
        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        TestCase.assertEquals("getController", uCallExpression.methodName)
        TestCase.assertNull(uCallExpression.receiverType)
    }

    fun checkUnderscoreOperatorForTypeArguments(myFixture: JavaCodeInsightTestFixture) {
        // example from https://kotlinlang.org/docs/generics.html#underscore-operator-for-type-arguments
        // modified to avoid using reflection (::class.java)
        myFixture.configureByText(
            "main.kt", """
                abstract class SomeClass<T> {
                    abstract fun execute() : T
                }

                class SomeImplementation : SomeClass<String>() {
                    override fun execute(): String = "Test"
                }

                class OtherImplementation : SomeClass<Int>() {
                    override fun execute(): Int = 42
                }

                object Runner {
                    inline fun <reified S: SomeClass<T>, T> run(instance: S) : T {
                        return instance.execute()
                    }
                }

                fun test() {
                    val i = SomeImplementation()
                    // T is inferred as String because SomeImplementation derives from SomeClass<String>
                    val s = Runner.run<_, _>(i)
                    assert(s == "Test")

                    val j = OtherImplementation()
                    // T is inferred as Int because OtherImplementation derives from SomeClass<Int>
                    val n = Runner.run<_, _>(j)
                    assert(n == 42)
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        uFile.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName != "run") return super.visitCallExpression(node)

                TestCase.assertEquals(2, node.typeArgumentCount)
                val firstTypeArg = node.typeArguments[0]
                val secondTypeArg = node.typeArguments[1]

                when (firstTypeArg.canonicalText) {
                    "SomeImplementation" -> {
                        TestCase.assertEquals("java.lang.String", secondTypeArg.canonicalText)
                    }
                    "OtherImplementation" -> {
                        TestCase.assertEquals("java.lang.Integer", secondTypeArg.canonicalText)
                    }
                    else -> TestCase.assertFalse("Unexpected $firstTypeArg", true)
                }

                return super.visitCallExpression(node)
            }
        })
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

    // Regression test from KT-59564
    fun checkExpressionTypeOfForEach(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                // !LANGUAGE: +RangeUntilOperator
                @file:OptIn(ExperimentalStdlibApi::class)
                fun test(a: Int, b: Int) {
                  for (i in a..<b step 1) {
                       println(i)
                  }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        uFile.accept(object : AbstractUastVisitor() {
            override fun visitForEachExpression(node: UForEachExpression): Boolean {
                when (val exp = node.iteratedValue.skipParenthesizedExprDown()) {
                    is UBinaryExpression -> {
                        TestCase.assertEquals("kotlin.ranges.IntProgression", exp.getExpressionType()?.canonicalText)
                        TestCase.assertEquals("kotlin.ranges.IntRange", exp.leftOperand.getExpressionType()?.canonicalText)
                    }
                }

                return super.visitForEachExpression(node)
            }
        })
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

    fun checkExpressionTypeForCallToInternalOperator(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                object Dependency {
                    internal operator fun unaryPlus() = Any()
                    operator fun unaryMinus() = Any()
                    operator fun not() = Any()
                }
                
                class OtherDependency {
                    internal operator fun inc() = this
                    operator fun dec() = this
                }
                
                fun test {
                    +Dependency
                    Dependency.unaryPlus()
                    -Dependency
                    Dependency.unaryMinus()
                    !Dependency
                    Dependency.not()
                    
                    var x = OtherDependency()
                    x++
                    x.inc()
                    x--
                    x.dec()
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        val binaryOperators = setOf("inc", "dec")

        uFile.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.isConstructorCall()) return super.visitCallExpression(node)

                if (node.methodName in binaryOperators) {
                    TestCase.assertEquals("OtherDependency", node.getExpressionType()?.canonicalText)
                } else {
                    TestCase.assertEquals("java.lang.Object", node.getExpressionType()?.canonicalText)
                }

                return super.visitCallExpression(node)
            }

            override fun visitPrefixExpression(node: UPrefixExpression): Boolean {
                TestCase.assertEquals("java.lang.Object", node.getExpressionType()?.canonicalText)

                return super.visitPrefixExpression(node)
            }

            override fun visitPostfixExpression(node: UPostfixExpression): Boolean {
                TestCase.assertEquals("OtherDependency", node.getExpressionType()?.canonicalText)

                return super.visitPostfixExpression(node)
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

    fun checkLambdaImplicitParameters(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                inline fun <T> fun0Consumer(f: () -> T): T {
                  return f()
                }
                
                inline fun <P, R> fun1Consumer(arg: P, f: (P) -> R): R {
                    return f(arg)
                }
                
                inline fun <P, R> fun1ExtConsumer(arg: P, f: P.() -> R): R {
                    return arg.f()
                }
                
                inline fun <P1, P2, R> fun2Consumer(arg1: P1, arg2: P2, f: (P1, P2) -> R): R {
                    return f(arg1, arg2)
                }
                
                inline fun <P1, P2, R> fun2ExtConsumer(arg1: P1, arg2: P2, f: P1.(P2) -> R): R {
                    return arg1.f(arg2)
                }
                
                fun test() {
                    fun0Consumer {
                        println("Function0")
                    }
                    fun1Consumer(42) {
                        println(it)
                    }
                    fun1ExtConsumer(42) {
                        println(this)
                    }
                    fun2Consumer(42, "42") { p1, p2 ->
                        println(p1.toString() == p2)
                    }
                    fun2ExtConsumer(42, "42") { 
                        println(this.toString() == it)
                    }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        uFile.accept(object : AbstractUastVisitor() {
            override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                val parameters = node.parameters
                val methodName = (node.uastParent as? UCallExpression)?.methodName
                TestCase.assertNotNull(methodName)

                when (methodName!!) {
                    "fun0Consumer" -> {
                        TestCase.assertTrue(parameters.isEmpty())
                    }
                    "fun1Consumer" -> {
                        TestCase.assertEquals(1, parameters.size)
                        val it = parameters.single()
                        TestCase.assertEquals("it", it.name)
                        TestCase.assertEquals("int", it.type.canonicalText)
                    }
                    "fun1ExtConsumer" -> {
                        TestCase.assertEquals(1, parameters.size)
                        val it = parameters.single()
                        TestCase.assertEquals("<this>", it.name)
                        TestCase.assertEquals("int", it.type.canonicalText)
                    }
                    "fun2Consumer" -> {
                        TestCase.assertEquals(2, parameters.size)
                        val p1 = parameters[0]
                        TestCase.assertEquals("p1", p1.name)
                        TestCase.assertEquals("int", p1.type.canonicalText)
                        val p2 = parameters[1]
                        TestCase.assertEquals("p2", p2.name)
                        TestCase.assertEquals("java.lang.String", p2.type.canonicalText)
                    }
                    "fun2ExtConsumer" -> {
                        TestCase.assertEquals(2, parameters.size)
                        val p1 = parameters[0]
                        TestCase.assertEquals("<this>", p1.name)
                        TestCase.assertEquals("int", p1.type.canonicalText)
                        val p2 = parameters[1]
                        TestCase.assertEquals("it", p2.name)
                        TestCase.assertEquals("java.lang.String", p2.type.canonicalText)
                    }
                    else -> TestCase.assertFalse("Unexpected $methodName", true)
                }

                return super.visitLambdaExpression(node)
            }
        })
    }

    fun checkLambdaBodyAsParentOfDestructuringDeclaration(myFixture: JavaCodeInsightTestFixture) {
        // KTIJ-24108
        myFixture.configureByText(
            "main.kt", """
                fun fi(data: List<String>) =
                    data.filter {
                        va<caret>l (a, b)
                    }
            """.trimIndent()
        )

        val destructuringDeclaration =
            myFixture.file.findElementAt(myFixture.caretOffset)
                ?.getParentOfType<KtDestructuringDeclaration>(strict = true)
                .orFail("Cannot find KtDestructuringDeclaration")

        val uDestructuringDeclaration =
            destructuringDeclaration.toUElement().orFail("Cannot convert to KotlinUDestructuringDeclarationExpression")

        TestCase.assertNotNull(uDestructuringDeclaration.uastParent)
    }

    fun checkUnclosedLazyValueBody(myFixture: JavaCodeInsightTestFixture) {
        // KTIJ-24092
        myFixture.configureByText(
            "main.kt", """
                val lazyValue: String by lazy {
                    println("Initializing lazy value")
                //}
                fun m<caret>ain() {
                }
            """.trimIndent()
        )

        val functionDeclaration =
            myFixture.file.findElementAt(myFixture.caretOffset)
                ?.getParentOfType<KtNamedFunction>(strict = false)
                .orFail("Cannot find KtNamedFunction")

        val uFunctionDeclaration = functionDeclaration.toUElement().orFail("Cannot convert to UElement")

        TestCase.assertNotNull(uFunctionDeclaration.uastParent)
    }

    fun checkIdentifierOfNullableExtensionReceiver(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                enum class SortOrder {
                  ASCENDING, DESCENDING, UNSORTED
                }

                fun <C> Comparator<C>?.withOrder(sortOrder: SortOrder): Comparator<C>? =
                  this?.let {
                    when (sortOrder) {
                      SortOrder.ASCENDING -> it
                      SortOrder.DESCENDING -> it.reversed()
                      SortOrder.UNSORTED -> null
                    }
                  }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val withOrder = uFile.findElementByTextFromPsi<UMethod>("withOrder", strict = false)
            .orFail("can't convert extension function: withOrder")
        val extensionReceiver = withOrder.uastParameters.first()
        val identifier = extensionReceiver.uastAnchor as? UIdentifier
        TestCase.assertNotNull(identifier)
    }

    fun checkReceiverTypeOfExtensionFunction(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Foo
                class Bar {
                  fun Foo.ext() {}
                  
                  fun test(f: Foo) {
                    f.ex<caret>t()
                  }
                }
            """.trimIndent()
        )
        val uCallExpression = myFixture.file.findElementAt(myFixture.caretOffset).toUElement().getUCallExpression()
            .orFail("cant convert to UCallExpression")
        TestCase.assertEquals("Foo", uCallExpression.receiverType?.canonicalText)
    }

    fun checkSourcePsiOfLazyPropertyAccessor(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                    var prop = "zzz"
                        internal get
                        private set
                    var lazyProp by lazy { setOf("zzz") }
                        private get
                        internal set
                }
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElement()!!
        uFile.accept(
            object : AbstractUastVisitor() {
                override fun visitMethod(node: UMethod): Boolean {
                    if (node.isConstructor) {
                        return super.visitMethod(node)
                    }
                    TestCase.assertTrue(node.sourcePsi?.text, node.sourcePsi is KtPropertyAccessor)
                    return super.visitMethod(node)
                }
            }
        )
    }

    fun checkTextRangeOfLocalVariable(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                fun foo(p: Any) {
                  val bar = { arg ->
                    arg == p
                  }
                  boo(p = b<caret>ar)
                }
                
                fun boo(p: (Any) -> Boolean): Boolean {
                  return p.invoke(42)
                }
            """.trimIndent()
        )
        val nameReferenceExpression = myFixture.file.findElementAt(myFixture.caretOffset)
            ?.getParentOfType<KtNameReferenceExpression>(strict = true)
            .orFail("Cannot find KtNameReferenceExpression")

        val uNameReferenceExpression = nameReferenceExpression.toUElementOfType<USimpleNameReferenceExpression>()
            .orFail("Cannot convert to KotlinUSimpleReferenceExpression")

        val localPsiVariable = uNameReferenceExpression.resolve()
            .orFail("Cannot find the local variable")

        // val bar = ...
        TestCase.assertNotNull(localPsiVariable.textRange)
        // boo(p = bar)
        TestCase.assertNotNull(uNameReferenceExpression.textRange)

        TestCase.assertNotSame(localPsiVariable.textRange, uNameReferenceExpression.textRange)
    }

    fun checkNameReferenceVisitInConstructorCall(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Foo
                fun test() {
                  val foo = Foo()
                }
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElement()!!
        var count = 0
        uFile.accept(
            object : AbstractUastVisitor() {
                var inConstructorCall: Boolean = false

                override fun visitCallExpression(node: UCallExpression): Boolean {
                    if (node.isConstructorCall()) {
                        inConstructorCall = true
                    }
                    return super.visitCallExpression(node)
                }

                override fun afterVisitCallExpression(node: UCallExpression) {
                    inConstructorCall = false
                    super.afterVisitCallExpression(node)
                }

                override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
                    if (inConstructorCall) {
                        count++
                        TestCase.assertEquals("Foo", node.resolvedName)
                    }
                    return super.visitSimpleNameReferenceExpression(node)
                }
            }
        )
        TestCase.assertEquals(1, count)
    }

    fun checkNoArgConstructorSourcePsi(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                open class SingleConstructor(val x: Int)

                class MultipleConstructorsOnlyPrimaryVisible(val x: Int) {
                  private constructor(x: Int, y: Int) : this(x + y)

                  internal constructor(x: Int, y: Int, z: Int) : this(x + y + z)
                }

                class MultipleConstructorsOnlySecondaryVisible private constructor(val x: Int) {
                  constructor(x: Int, y: Int) : this(x + y)

                  internal constructor(x: Int, y: Int, z: Int) : this(x + y + z)
                }

                // multiple constructors
                open class MultipleVisibleConstructors(val x: Int) {
                  constructor(x: Int, y: Int) : this(x + y)
                }

                // multiple constructors
                class MultipleVisibleConstructorsBothSecondary private constructor(val x: Int) {
                  constructor(x: Int, y: Int) : this(x + y)

                  constructor(x: Int, y: Int, z: Int) : this(x + y + z)
                }

                // multiple constructors
                class MultipleVisibleConstructorsNotFromSuperclass(x: Int) : SingleConstructor(x) {
                  constructor(x: Int, y: Int) : this(x + y)
                }

                // multiple constructors
                class MultipleVisibleConstructorsFromSuperclass(x: Int) : MultipleVisibleConstructors(x) {
                  constructor(x: Int, y: Int) : this(x + y)
                }

                // If _all_ of a constructor's arguments have a default value,
                // Kotlin will generate a default no-arg constructor as well, but! with the same PSI
                class ConstructorWithAllDefaultArgs(val x: Int = 0)

                class ConstructorWithAllDefaultArgsAndJvmOverloads
                @JvmOverloads
                constructor(val x: Int = 0)

                class ConstructorWithSomeDefaultArgs(val x: Int, val y: Int = 0)

                class ConstructorWithSomeDefaultArgsAndJvmOverloads
                @JvmOverloads
                constructor(val x: Int, val y: Int = 0)

                // multiple constructors
                class ConstructorWithAllDefaultArgsAndSecondaryConstructor(val x: Int = 0) {
                  constructor(x: Int, y: Int) : this(x + y)
                }

                // multiple constructors
                class ConstructorWithSomeDefaultArgsAndSecondaryConstructor(val x: Int, val y: Int = 0) {
                  constructor(x: Int, y: Int, z: Int) : this(x + y, z)
                }
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElementOfType<UFile>()!!
        val expectedMultipleConstructors = listOf(
            "MultipleVisibleConstructors",
            "MultipleVisibleConstructorsBothSecondary",
            "MultipleVisibleConstructorsNotFromSuperclass",
            "MultipleVisibleConstructorsFromSuperclass",
            "ConstructorWithAllDefaultArgsAndSecondaryConstructor",
            "ConstructorWithSomeDefaultArgsAndSecondaryConstructor",
        )
        uFile.accept(
            object : AbstractUastVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    val count = node.getNonPrivateConstructorCount()
                    if (node.name in expectedMultipleConstructors) {
                        TestCase.assertTrue("${node.name}: $count", count > 1)
                    } else {
                        TestCase.assertEquals("${node.name}: $count", 1, count)
                    }
                    return super.visitClass(node)
                }

                private fun UClass.getNonPrivateConstructorCount(): Int {
                    val declaredSourceConstructors =
                        this.methods
                            .filter { it.isConstructor && it.sourcePsi != null }
                            .distinctBy { System.identityHashCode(it.sourcePsi) }
                    return declaredSourceConstructors.count {
                        it.visibility != UastVisibility.PRIVATE && !hasInternalModifier(it)
                    }
                }

                private fun hasInternalModifier(owner: PsiModifierListOwner): Boolean {
                    val sourcePsi = if (owner is UElement) owner.sourcePsi else owner.unwrapped
                    return sourcePsi is KtModifierListOwner && sourcePsi.hasModifier(KtTokens.INTERNAL_KEYWORD)
                }
            }
        )
    }

    fun checkNullLiteral(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                fun test() {
                  val foo : Any? = null
                }
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElementOfType<UFile>()!!
        var count = 0
        uFile.accept(
            object : AbstractUastVisitor() {
                override fun visitLiteralExpression(node: ULiteralExpression): Boolean {
                    TestCase.assertTrue(node.isNull)
                    TestCase.assertEquals("null", node.getExpressionType()?.canonicalText)
                    count++
                    return super.visitLiteralExpression(node)
                }
            }
        )
        TestCase.assertEquals(1, count)
    }

    fun checkStringConcatInAnnotationValue(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """
                 import java.lang.annotation.ElementType;
                 import java.lang.annotation.Retention;
                 import java.lang.annotation.RetentionPolicy;
                 import java.lang.annotation.Target;

                 @Retention(RetentionPolicy.CLASS)
                 @Target({ElementType.METHOD})
                 public @interface MyAnnotation {
                     String[] password() default {};
                 }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                @MyAnnotation(
                  password = [
                    "nananananana, " +
                      "batman"
                  ]
                )
                fun t<caret>est() {}
            """.trimIndent()
        )
        val uMethod = myFixture.file.findElementAt(myFixture.caretOffset).toUElement()?.getParentOfType<UMethod>()
            .orFail("cant convert to UMethod")
        TestCase.assertNotNull(uMethod)
        val anno = uMethod.annotations.single()
        val attributeValue = anno.findAttributeValue("password")
        TestCase.assertNotNull(attributeValue)
        val initializer = (attributeValue as PsiArrayInitializerMemberValue).initializers.single()
        val uExpression = initializer.toUElementOfType<UExpression>()
        val uEval = uExpression?.evaluate()
        TestCase.assertEquals("nananananana, batman", uEval)
    }

    fun checkLocalPropertyInitializerEvaluation_String(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                  val foo = "foo"
                  
                  fun test(): String {
                    val bar = "bar"
                    return foo + bar
                  }
                  
                  fun poly(): String {
                    val na = "na"
                    val b = "batman"
                    return na + na + na + na + na + na + na + na + ", " + b
                  }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElementOfType<UFile>()!!
        val names = listOf("foo", "bar", "na", "batman")
        uFile.accept(
            object : AbstractUastVisitor() {
                override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
                    val eval = node.evaluate()
                    TestCase.assertTrue(eval?.toString() ?: "<null>", eval in names)
                    return super.visitSimpleNameReferenceExpression(node)
                }

                override fun visitReturnExpression(node: UReturnExpression): Boolean {
                    val eval = node.returnExpression?.evaluate()
                    if ((node.jumpTarget as? UMethod)?.name == "poly") {
                        TestCase.assertEquals(eval?.toString() ?: "<null>", "nananananananana, batman", eval)
                    } else {
                        TestCase.assertEquals(eval?.toString() ?: "<null>", "foobar", eval)
                    }
                    return super.visitReturnExpression(node)
                }
            }
        )
    }

    fun checkLocalPropertyInitializerEvaluation_Numeric(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Test {
                  val foo = 1

                  fun test() {
                    val bar = 41
                    foo + bar
                    val baz = 42
                    foo * baz * foo
                    baz / foo / foo
                    val qaz = 43
                    qaz - foo
                  }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElementOfType<UFile>()!!
        uFile.accept(
            object : AbstractUastVisitor() {
                override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                    val eval = node.evaluate()
                    TestCase.assertEquals(node.sourcePsi?.text, 42, eval)
                    return super.visitBinaryExpression(node)
                }
            }
        )
    }
}