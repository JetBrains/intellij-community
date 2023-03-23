// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import junit.framework.TestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.test.env.findElementByTextFromPsi
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor

// NB: Similar to [UastResolveApiFixtureTestBase], but focusing on light classes, not `resolve`
interface LightClassBehaviorTestBase : UastPluginSelection {
    // NB: ported [LightClassBehaviorTest#testIdentifierOffsets]
    fun checkIdentifierOffsets(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "test.kt", """
            class A {
                fun foo() {}
            }
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElement()!!

        val foo = uFile.findElementByTextFromPsi<UMethod>("foo", strict = false)
            .orFail("can't find fun foo")
        val fooMethodName = foo.javaPsi.nameIdentifier!!

        val offset = fooMethodName.textOffset
        val range = fooMethodName.textRange

        TestCase.assertTrue(offset > 0)
        TestCase.assertEquals(offset, range.startOffset)
    }

    // NB: ported [LightClassBehaviorTest#testPropertyAccessorOffsets]
    fun checkPropertyAccessorOffsets(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "test.kt", """
            class A {
                var a: Int
                    get() = 5
                    set(v) {}
            }
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElement()!!
        val aClass = uFile.findElementByTextFromPsi<UClass>("A", strict = false)
            .orFail("can't find class A")
        val getAMethod = aClass.javaPsi.findMethodsByName("getA").single() as PsiMethod
        val setAMethod = aClass.javaPsi.findMethodsByName("setA").single() as PsiMethod

        val ktClass = aClass.sourcePsi as KtClassOrObject
        val ktProperty = ktClass.declarations.filterIsInstance<KtProperty>().single()

        TestCase.assertNotSame(getAMethod.textOffset, setAMethod.textOffset)

        TestCase.assertTrue(getAMethod.textOffset > 0)
        TestCase.assertNotSame(getAMethod.textOffset, ktProperty.textOffset)
        TestCase.assertEquals(getAMethod.textOffset, ktProperty.getter?.textOffset)
        TestCase.assertEquals(getAMethod.textOffset, getAMethod.textRange.startOffset)

        TestCase.assertTrue(setAMethod.textOffset > 0)
        TestCase.assertNotSame(setAMethod.textOffset, ktProperty.textOffset)
        TestCase.assertEquals(setAMethod.textOffset, ktProperty.setter?.textOffset)
        TestCase.assertEquals(setAMethod.textOffset, setAMethod.textRange.startOffset)
        TestCase.assertEquals("set(v) {}", setAMethod.text)
    }

    fun checkFunctionModifierListOffsets(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "test.kt", """
            annotation class MyAnnotation
            class A {
                @MyAnnotation
                fun foo() {}
            }
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElement()!!

        val foo = uFile.findElementByTextFromPsi<UMethod>("foo", strict = false)
            .orFail("can't find fun foo")
        val fooMethodJavaPsiModifierList = foo.javaPsi.modifierList
        TestCase.assertTrue(fooMethodJavaPsiModifierList.textOffset > 0)
        TestCase.assertFalse(fooMethodJavaPsiModifierList.textRange.isEmpty)
        TestCase.assertEquals(fooMethodJavaPsiModifierList.textOffset, fooMethodJavaPsiModifierList.textRange.startOffset)
        TestCase.assertEquals("@MyAnnotation", fooMethodJavaPsiModifierList.text)

        val fooMethodSourcePsiModifierList = (foo.sourcePsi as KtModifierListOwner).modifierList!!
        TestCase.assertTrue(fooMethodSourcePsiModifierList.textOffset > 0)
        TestCase.assertFalse(fooMethodSourcePsiModifierList.textRange.isEmpty)
        TestCase.assertEquals(fooMethodSourcePsiModifierList.textOffset, fooMethodSourcePsiModifierList.textRange.startOffset)
        TestCase.assertEquals("@MyAnnotation", fooMethodSourcePsiModifierList.text)

        TestCase.assertEquals(fooMethodJavaPsiModifierList.textOffset, fooMethodSourcePsiModifierList.textOffset)
        TestCase.assertEquals(fooMethodJavaPsiModifierList.textRange, fooMethodSourcePsiModifierList.textRange)
    }

    fun checkPropertyAccessorModifierListOffsets(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "test.kt", """
            annotation class MyAnnotation
            class Foo {
                var a: Int
                    @MyAnnotation
                    get() = 5
                    set(v) {}
            }
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElement()!!
        val aClass = uFile.findElementByTextFromPsi<UClass>("Foo", strict = false)
            .orFail("can't find class Foo")
        val getAMethod = aClass.javaPsi.findMethodsByName("getA").single() as PsiMethod
        val getAMethodModifierList = getAMethod.modifierList

        TestCase.assertTrue(getAMethodModifierList.textOffset > 0)
        TestCase.assertFalse(getAMethodModifierList.textRange.isEmpty)
        TestCase.assertEquals(getAMethodModifierList.textOffset, getAMethodModifierList.textRange.startOffset)
        TestCase.assertEquals("@MyAnnotation", getAMethodModifierList.text)

        val ktClass = aClass.sourcePsi as KtClassOrObject
        val ktProperty = ktClass.declarations.filterIsInstance<KtProperty>().single()
        val ktPropertyAccessorModifierList = ktProperty.getter!!.modifierList!!

        TestCase.assertEquals(getAMethodModifierList.textOffset, ktPropertyAccessorModifierList.textOffset)
        TestCase.assertEquals(getAMethodModifierList.textRange, ktPropertyAccessorModifierList.textRange)
    }

    fun checkThrowsList(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                abstract class Base
                
                class MyException : Exception()

                class Test
                @Throws(MyException::class)
                constructor(
                    private val p1: Int
                ) : Base() {
                    @Throws(MyException::class)
                    fun readSomething(file: File) {
                      throw MyException()
                    }

                    @get:Throws(MyException::class)
                    val foo : String = "42"
                    
                    val boo : String = "42"
                        @Throws(MyException::class)
                        get
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val aClass = uFile.findElementByTextFromPsi<UClass>("Test", strict = false)
            .orFail("can't find class Test")

        val visitedMethod = mutableListOf<UMethod>()
        aClass.accept(object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
                visitedMethod.add(node)

                val throwTypes = node.javaPsi.throwsList.referencedTypes
                TestCase.assertEquals(node.name, 1, throwTypes.size)
                TestCase.assertEquals("MyException", throwTypes.single().className)

                return super.visitMethod(node)
            }
        })

        TestCase.assertNotNull(visitedMethod.singleOrNull { it.isConstructor })
        TestCase.assertNotNull(visitedMethod.singleOrNull { it.name == "readSomething" })
        TestCase.assertNotNull(visitedMethod.singleOrNull { it.name == "getFoo" })
        TestCase.assertNotNull(visitedMethod.singleOrNull { it.name == "getBoo" })
    }

    private fun checkPsiType(psiType: PsiType, fqName: String = "TypeAnnotation") {
        TestCase.assertEquals(1, psiType.annotations.size)
        val annotation = psiType.annotations[0]
        TestCase.assertEquals(fqName, annotation.qualifiedName)
    }

    fun checkAnnotationOnPsiType(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                @Target(AnnotationTarget.TYPE)
                annotation class TypeAnnotation
                
                interface State<out T> {
                    val value: T
                }
                
                fun test(
                    i : @TypeAnnotation Int?,
                    s : @TypeAnnotation String?,
                    vararg vs : @TypeAnnotation Any,
                ): @TypeAnnotation State<String> {
                    return object : State<String> {
                        override val value: String = i?.toString() ?: s ?: "42"
                    }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        val test = uFile.findElementByTextFromPsi<UMethod>("test", strict = false)
            .orFail("can't find fun test")
        val lightMethod = test.javaPsi

        TestCase.assertNotNull(lightMethod.returnType)
        checkPsiType(lightMethod.returnType!!)

        lightMethod.parameterList.parameters.forEach { psiParameter ->
            val psiTypeToCheck = (psiParameter.type as? PsiArrayType)?.componentType ?: psiParameter.type
            checkPsiType(psiTypeToCheck)
        }
    }

    fun checkAnnotationOnPsiTypeArgument(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                @Target(AnnotationTarget.TYPE)
                annotation class TypeAnnotation
                
                fun test(
                    ins : List<@TypeAnnotation Int>,
                ): Array<@TypeAnnotation String> {
                    return ins.map { it.toString() }.toTypedArray()
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        val test = uFile.findElementByTextFromPsi<UMethod>("test", strict = false)
            .orFail("can't find fun test")
        val lightMethod = test.javaPsi

        fun firstTypeArgument(psiType: PsiType?): PsiType? =
            (psiType as? PsiClassType)?.parameters?.get(0)
                ?: (psiType as? PsiArrayType)?.componentType

        TestCase.assertNotNull(lightMethod.returnType)
        checkPsiType(firstTypeArgument(lightMethod.returnType)!!)

        lightMethod.parameterList.parameters.forEach { psiParameter ->
            checkPsiType(firstTypeArgument(psiParameter.type)!!)
        }
    }

}