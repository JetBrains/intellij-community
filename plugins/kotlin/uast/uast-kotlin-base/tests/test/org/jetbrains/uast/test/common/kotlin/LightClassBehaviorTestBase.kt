// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.common.kotlin

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.patterns.PlatformPatterns
import com.intellij.platform.uast.testFramework.env.findElementByTextFromPsi
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceContributorEP
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.ProcessingContext
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

// NB: Similar to [UastResolveApiFixtureTestBase], but focusing on light classes, not `resolve`
interface LightClassBehaviorTestBase {
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

    fun checkLocalClassCaching(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "test.kt",
            """
            fun foo() {
              class Bar() {
                fun baz() {}
                val property = 43
                constructor(i: Int): this()
                
                init {
                  42
                }
              }
            }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        fun findDeclaration(): Set<KtNamedDeclaration> {
            val clazz = uFile.findElementByTextFromPsi<UClass>("Bar", strict = false).orFail("can't find class Bar")
            return clazz.uastDeclarations.map { it.javaPsi?.unwrapped as KtNamedDeclaration }.toSet()
        }

        val declarationsBefore = findDeclaration()
        val lightElementsBefore = mutableSetOf<PsiElement>()
        for (namedDeclaration in declarationsBefore) {
            val lightElements = namedDeclaration.toLightElements()
            if (lightElements.isEmpty()) error("Light elements for ${namedDeclaration.name} is not found")

            lightElementsBefore += lightElements
            for (lightElement in lightElements) {
                TestCase.assertTrue(lightElement.isValid)
            }

            runUndoTransparentWriteAction {
                val ktPsiFactory = KtPsiFactory(myFixture.project)
                val text = namedDeclaration.text
                val newDeclaration = when (namedDeclaration) {
                    is KtPrimaryConstructor -> ktPsiFactory.createPrimaryConstructor(text)
                    is KtSecondaryConstructor -> ktPsiFactory.createSecondaryConstructor(text)
                    else -> ktPsiFactory.createDeclaration<KtNamedDeclaration>(text)
                }

                namedDeclaration.replace(newDeclaration)
            }

            for (namedElement in lightElements) {
                TestCase.assertFalse(namedElement.isValid)
            }
        }

        val recreatedDeclarations = findDeclaration()
        for (namedDeclaration in recreatedDeclarations) {
            TestCase.assertTrue(namedDeclaration.name, namedDeclaration !in declarationsBefore)

            val lightElements = namedDeclaration.toLightElements()
            if (lightElements.isEmpty()) error("Light elements for ${namedDeclaration.name} is not found")
            for (lightElement in lightElements) {
                TestCase.assertTrue(lightElement.isValid)
                TestCase.assertTrue(lightElement !in lightElementsBefore)
            }
        }
    }

    fun annotationsOnClassCaching(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "test.kt", """
            package test
                
            annotation class MyAnnotation

            @MyAnnotation
            class TestClass {
                fun method() {}
            }
            """.trimIndent()
        )

        val annotationQualifiedName = "test.MyAnnotation"
        
        val uFile = myFixture.file.toUElement()!!
        val uClass = uFile.findElementByTextFromPsi<UClass>("TestClass", strict = false)
            .orFail("can't find class TestClass")
        
        val lightClass = uClass.javaPsi
        val lightAnnotationBeforeModification = lightClass.modifierList?.findAnnotation(annotationQualifiedName)
        
        TestCase.assertNotNull(
            "Light class should have annotation in the modifier list at the start",
            lightAnnotationBeforeModification
        )
        
        val ktAnnotation = lightAnnotationBeforeModification?.unwrapped as KtAnnotationEntry
        runUndoTransparentWriteAction {
            // Remove the annotation from the Kotlin PSI
            ktAnnotation.delete()
        }

        val lightAnnotationAfterModification = lightClass.modifierList?.findAnnotation(annotationQualifiedName)
        TestCase.assertNull(
            "Light class should not have annotation in the modifier list after its removal",
            lightAnnotationAfterModification
        )
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

    fun checkFinalModifierOnEnumMembers(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                enum class Event {
                  ON_CREATE, ON_START, ON_STOP, ON_DESTROY;
                  companion object {
                    @JvmStatic
                    fun upTo(state: State): Event? {
                      return when(state) {
                        State.ENQUEUED -> ON_CREATE
                        State.RUNNING -> ON_START
                        State.BLOCKED -> ON_STOP
                        else -> null
                      }
                    }
                  }
                }
                
                enum class State {
                  ENQUEUED, RUNNING, SUCCEEDED, FAILED, BLOCKED, CANCELLED;
                  val isFinished: Boolean
                    get() = this == SUCCEEDED || this == FAILED || this == CANCELLED
                  fun isAtLeast(state: State): Boolean {
                    return compareTo(state) >= 0
                  }
                  companion object {
                    fun done(state: State) = state.isFinished
                  }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        val upTo = uFile.findElementByTextFromPsi<UMethod>("upTo", strict = false)
            .orFail("can't find fun upTo")
        TestCase.assertTrue(upTo.javaPsi.hasModifier(JvmModifier.FINAL))
        TestCase.assertTrue(upTo.javaPsi.containingClass!!.hasModifier(JvmModifier.FINAL))
        TestCase.assertTrue(upTo.javaPsi.parameterList.parameters[0].annotations.any { it.isNotNull })

        val isFinished = uFile.findElementByTextFromPsi<UMethod>("isFinished", strict = false)
            .orFail("can't find accessor isFinished")
        TestCase.assertTrue(isFinished.javaPsi.hasModifier(JvmModifier.FINAL))
        TestCase.assertTrue(isFinished.javaPsi.containingClass!!.hasModifier(JvmModifier.FINAL))

        val isAtLeast = uFile.findElementByTextFromPsi<UMethod>("isAtLeast", strict = false)
            .orFail("can't find fun isAtLeast")
        TestCase.assertTrue(isAtLeast.javaPsi.hasModifier(JvmModifier.FINAL))
        TestCase.assertTrue(isAtLeast.javaPsi.containingClass!!.hasModifier(JvmModifier.FINAL))
        TestCase.assertTrue(isAtLeast.javaPsi.parameterList.parameters[0].annotations.any { it.isNotNull })

        val done = uFile.findElementByTextFromPsi<UMethod>("done", strict = false)
            .orFail("can't find fun done")
        TestCase.assertTrue(done.javaPsi.hasModifier(JvmModifier.FINAL))
        TestCase.assertTrue(done.javaPsi.containingClass!!.hasModifier(JvmModifier.FINAL))
        TestCase.assertTrue(done.javaPsi.parameterList.parameters[0].annotations.any { it.isNotNull })
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

    fun checkComparatorInheritor(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class Foo(val x : Int)
                class FooComparator : Comparator<Foo> {
                  override fun compare(firstFoo: Foo, secondFoo: Foo): Int =
                    firstFoo.x - secondFoo.x
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val fooComparator = uFile.findElementByTextFromPsi<UClass>("FooComparator", strict = false)
            .orFail("can't find class FooComparator")

        val lc = fooComparator.javaPsi
        TestCase.assertTrue(lc.extendsList?.referenceElements?.isEmpty() == true)
        TestCase.assertTrue(lc.implementsList?.referenceElements?.size == 1)
        TestCase.assertEquals("java.util.Comparator<Foo>", lc.implementsList?.referenceElements?.single()?.reference?.canonicalText)
    }

    fun checkBoxedReturnTypeWhenOverridingNonPrimitive(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                abstract class ActivityResultContract<I, O> {
                  abstract fun parseResult(resultCode: Int, intent: Intent?): O
                }

                interface Intent

                class StartActivityForResult : ActivityResultContract<Intent, Boolean>() {
                  override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
                    return resultCode == 42
                  }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val sub = uFile.findElementByTextFromPsi<UClass>("StartActivityForResult", strict = false)
            .orFail("can't find class StartActivityForResult")

        val mtd = sub.methods.find { it.name == "parseResult" }
            .orFail("can't find method parseResult")

        TestCase.assertEquals("java.lang.Boolean", mtd.returnType?.canonicalText)
    }

    private fun checkPsiType(psiType: PsiType, fqName: String = "TypeAnnotation") {
        val annotations = psiType.annotations.asList()
        val nullabilityAnnotation = annotations.find {
            when (it.qualifiedName) {
                JvmAnnotationNames.JETBRAINS_NULLABLE_ANNOTATION.asString(), JvmAnnotationNames.JETBRAINS_NOT_NULL_ANNOTATION.asString() -> true
                else -> false
            }
        }

        TestCase.assertEquals(1 + (1.takeIf { nullabilityAnnotation != null } ?: 0), psiType.annotations.size)
        val annotation = annotations.minus(listOfNotNull(nullabilityAnnotation)).single()
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

    fun checkUpperBoundWildcardForCtor(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                class FrameData(
                  frameStartNanos: Long,
                  frameDurationUiNanos: Long,
                  isJank: Boolean,
                  val states: List<StateInfo>
                )
                
                class StateInfo(val key: String, val value: String)
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        val cls = uFile.findElementByTextFromPsi<UClass>("FrameData", strict = false)
            .orFail("can't find class FrameData")
        val ctor = cls.javaPsi.constructors.single()
        val states = ctor.parameterList.parameters.last()
        TestCase.assertEquals("java.util.List<StateInfo>", states.type.canonicalText)
    }

    fun checkUpperBoundWildcardForEnum(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                enum class PowerCategoryDisplayLevel {
                  BREAKDOWN, TOTAL
                }

                enum class PowerCategory {
                  CPU, MEMORY
                }

                class PowerMetric {
                  companion object {
                    @JvmStatic
                    fun Battery(): Type.Battery {
                      return Type.Battery()
                    }

                    @JvmStatic
                    fun Energy(
                      categories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                    ): Type.Energy {
                      return Type.Energy(categories)
                    }

                    @JvmStatic
                    fun Power(
                      categories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                    ): Type.Power {
                      return Type.Power(categories)
                    }
                  }

                  sealed class Type(var categories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()) {
                    class Power(
                      powerCategories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                    ) : Type(powerCategories)

                    class Energy(
                      energyCategories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                    ) : Type(energyCategories)

                    class Battery : Type()
                  }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!

        uFile.accept(object : AbstractUastVisitor() {
            override fun visitParameter(node: UParameter): Boolean {
                val lc = node.javaPsi as? PsiParameter ?: return super.visitParameter(node)

                val t = lc.type.canonicalText
                if (t.contains("Map")) {
                    TestCase.assertEquals("java.util.Map<PowerCategory,? extends PowerCategoryDisplayLevel>", t)
                }

                return super.visitParameter(node)
            }
        })
    }

    fun checkUpperBoundWildcardForVar(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                abstract class RoomDatabase {
                  @JvmField
                  protected var mCallbacks: List<Callback>? = null
                  
                  abstract class Callback {
                    open fun onCreate(db: RoomDatabase) {}
                  }
                }

                val sum: (Int) -> Int = { x: Int -> sum(x - 1) + x }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val fld = uFile.findElementByTextFromPsi<UField>("mCallbacks", strict = false)
            .orFail("can't find var mCallbacks")
        val mCallbacks = fld.javaPsi as? PsiField
        TestCase.assertEquals("java.util.List<? extends RoomDatabase.Callback>", mCallbacks?.type?.canonicalText)

        val top = uFile.findElementByTextFromPsi<UField>("sum", strict = false)
            .orFail("can't find val sum")
        val sum = top.javaPsi as? PsiField
        TestCase.assertEquals("kotlin.jvm.functions.Function1<java.lang.Integer,java.lang.Integer>", sum?.type?.canonicalText)
    }

    fun checkUpperBoundForRecursiveTypeParameter(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                interface Alarm {
                  interface Builder<Self : Builder<Self>> {
                    fun build(): Alarm
                  }
                }

                abstract class AbstractAlarm<
                    Self : AbstractAlarm<Self, Builder>, Builder : AbstractAlarm.Builder<Builder, Self>>
                internal constructor(
                    val identifier: String,
                ) : Alarm {
                  abstract class Builder<Self : Builder<Self, Built>, Built : AbstractAlarm<Built, Self>> : Alarm.Builder<Self> {
                    private var identifier: String = ""

                    fun setIdentifier(text: String): Self {
                      this.identifier = text
                      return this as Self
                    }

                    final override fun build(): Built = TODO()
                  }
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val abstractAlarm = uFile.findElementByTextFromPsi<UClass>("AbstractAlarm", strict = false)
            .orFail("can't find AbstractAlarm")
        val builder = abstractAlarm.innerClasses.find { it.name == "Builder" }
            .orFail("can't find AbstractAlarm.Builder")
        TestCase.assertEquals(2, builder.javaPsi.typeParameters.size)
        val self = builder.javaPsi.typeParameters[0]
        TestCase.assertEquals(
            "AbstractAlarm.Builder<Self,Built>",
            self.bounds.joinToString { (it as? PsiType)?.canonicalText ?: "??" }
        )
        val built = builder.javaPsi.typeParameters[1]
        TestCase.assertEquals(
            "AbstractAlarm<Built,Self>",
            built.bounds.joinToString { (it as? PsiType)?.canonicalText ?: "??" }
        )
    }

    fun checkDefaultValueOfAnnotation_Kotlin(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "main.kt", """
                annotation class IntDef(
                    vararg val value: Int = [],
                    val flag: Boolean = false,
                    val open: Boolean = false
                )
                
                @IntDef(value = [DisconnectReasons.ENGINE_DIED, DisconnectReasons.ENGINE_DETACHED])
                annotation class DisconnectReason

                object DisconnectReasons {
                    const val ENGINE_DIED: Int = 1
                    const val ENGINE_DETACHED: Int = 2
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val klass = uFile.findElementByTextFromPsi<UClass>("class DisconnectReason", strict = false)
            .orFail("can't convert to UClass")
        val lc = klass.uAnnotations.single().javaPsi!!
        val intValues = (lc.findAttributeValue("value") as? PsiArrayInitializerMemberValue)?.initializers
        TestCase.assertEquals(
            "[1, 2]",
            intValues?.joinToString(separator = ", ", prefix = "[", postfix = "]") { annoValue ->
                (annoValue as? PsiLiteralExpression)?.value?.toString() ?: annoValue.text
            }
        )

        val flagValue = (lc.findAttributeValue("flag") as? PsiLiteralExpression)?.value
        TestCase.assertEquals("false", flagValue?.toString())
    }

    fun checkDefaultValueOfAnnotation_Java(myFixture: JavaCodeInsightTestFixture) {
        myFixture.addClass(
            """
                import java.lang.annotation.Annotation;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;

                @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
                public @interface MyRestrictedApi {
                  String explanation();
                  Class<? extends Annotation>[] allowlistAnnotations() default {};
                  boolean allowedInTestonlyTargets() default false;
                }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.kt", """
                @Target(AnnotationTarget.FUNCTION) annotation class KAllowlist

                class Test {
                  @MyRestrictedApi(
                    explanation = "umbrella",
                    allowlistAnnotations = [KAllowlist::class],
                  )
                  fun foo() {}
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val foo = uFile.findElementByTextFromPsi<UMethod>("fun foo", strict = false)
            .orFail("can't convert to UMethod")
        val lc = foo.uAnnotations.single().javaPsi!!
        val annos = (lc.findAttributeValue("allowlistAnnotations") as? PsiArrayInitializerMemberValue)?.initializers
        TestCase.assertEquals(
            "[KAllowlist]",
            annos?.joinToString(separator = ", ", prefix = "[", postfix = "]") { annoValue ->
                (annoValue as? PsiClassObjectAccessExpression)?.type?.canonicalText ?: annoValue.text
            }
        )

        val flagValue = (lc.findAttributeValue("allowedInTestonlyTargets") as? PsiLiteralExpression)?.value
        TestCase.assertEquals("false", flagValue?.toString())
    }

    fun checkAnnotationParameterReference(myFixture: JavaCodeInsightTestFixture) {
        PsiReferenceContributor.EP_NAME.point.registerExtension(
            PsiReferenceContributorEP().apply {
                implementationClass = MyPsiReferenceContributor::class.java.name
                pluginDescriptor = DefaultPluginDescriptor("test")
            },
            myFixture.testRootDisposable
        )
        myFixture.configureByText(
            "main.kt",
            """
                @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
                @Retention(AnnotationRetention.SOURCE)
                annotation class NamedAnno(val name: String)
                
                class User {
                    @NamedAnno(name = "aName") lateinit var fullName: String
                }
            """.trimIndent()
        )

        val uFile = myFixture.file.toUElement()!!
        val lc = uFile.findElementByTextFromPsi<UClass>("class User", strict = false)
            .orFail("can't convert to UClass").javaPsi
        val fullNameProperty = lc.findFieldByName("fullName", false) ?: error("unable to look up property `fullName`")
        val annotation = fullNameProperty.modifierList?.annotations?.singleOrNull()
            ?: error("expected the only one annotation")
        val annotationMemberValue =
            annotation.findDeclaredAttributeValue("name") as? PsiLiteralValue ?: error("expected PsiLiteralValue annotation")
        TestCase.assertEquals("aName", annotationMemberValue.value)
        val references = annotationMemberValue.references
        TestCase.assertEquals(1, references.size)
        TestCase.assertTrue(references[0].resolve() != null)
    }

    class MyPsiReferenceContributor : PsiReferenceContributor(),
                                      KeyedLazyInstance<PsiReferenceContributor> {
        override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
            val referenceProvider = object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val text = (element as KtStringTemplateExpression).text
                    return if (text == "\"aName\"") {
                        arrayOf(object : PsiReferenceBase<PsiElement?>(element, false) {
                            override fun resolve(): PsiElement? {
                                return element.originalElement
                            }
                        })
                    } else {
                        PsiReference.EMPTY_ARRAY
                    }
                }
            }
            registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
                referenceProvider,
                PsiReferenceRegistrar.HIGHER_PRIORITY
            )
        }

        override fun getKey(): String = "custom"

        override fun getInstance(): PsiReferenceContributor = this
    }

    fun checkContainingFile(myFixture: JavaCodeInsightTestFixture) {
        myFixture.configureByText(
            "test.kt", """
            class A
            class B
            fun foo(){}
            """.trimIndent()
        )
        val uFile = myFixture.file.toUElement()!!
        val aClass = uFile.findElementByTextFromPsi<UClass>("A", strict = false).orFail("can't find class A")
        val bClass = uFile.findElementByTextFromPsi<UClass>("B", strict = false).orFail("can't find class B")
        val fooFunction = uFile.findElementByTextFromPsi<UMethod>("foo", strict = false).orFail("can't find function foo")
        val aContainingFile = aClass.javaPsi.containingFile
        val bContainingFile = bClass.javaPsi.containingFile
        val fooContainingFile = fooFunction.javaPsi.containingFile
        TestCase.assertFalse(aContainingFile is KtFile)
        TestCase.assertFalse(bContainingFile is KtFile)
        TestCase.assertFalse(fooContainingFile is KtFile)
        TestCase.assertFalse("different instances of fakeFile are expected", aContainingFile === bContainingFile)
        TestCase.assertFalse("different instances of fakeFile are expected", aContainingFile === fooContainingFile)
        TestCase.assertTrue("fakeFiles should be equal to each other", aContainingFile == bContainingFile)
        TestCase.assertTrue("fakeFiles should be equal to each other", aContainingFile == fooContainingFile)
    }

    fun checkContainingFileInFacadeFiles(myFixture: JavaCodeInsightTestFixture) {
        val file1 = myFixture.configureByText(
            "multifile1.kt", """
            @file:kotlin.jvm.JvmName("Multifile")
            @file:kotlin.jvm.JvmMultifileClass

            package declaration

            fun foo(): Int = 42
            """.trimIndent()
        )
        val file2 = myFixture.configureByText(
            "multifile2.kt", """
            @file:kotlin.jvm.JvmName("Multifile")
            @file:kotlin.jvm.JvmMultifileClass

            package declaration

            fun String.buzz(): String = "$this... zzz..."
            """.trimIndent()
        )
        val uFile1 = file1.toUElement()!!
        val uFile2 = file2.toUElement()!!
        val aClass = uFile1.findElementByTextFromPsi<UClass>("foo", strict = false).orFail("can't find function foo")
        val bClass = uFile2.findElementByTextFromPsi<UClass>("buzz", strict = false).orFail("can't find function buzz")
        val aContainingFile = aClass.javaPsi.containingFile
        val bContainingFile = bClass.javaPsi.containingFile
        TestCase.assertFalse(aContainingFile is KtFile)
        TestCase.assertFalse(bContainingFile is KtFile)
        TestCase.assertFalse("different instances of fakeFile are expected", aContainingFile === bContainingFile)
        TestCase.assertTrue("fakeFiles should be equal to each other", aContainingFile == bContainingFile)
    }

}