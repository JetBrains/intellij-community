// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin.analysis

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.uast.*
import org.jetbrains.uast.analysis.UDeclarationFact
import org.jetbrains.uast.analysis.UExpressionFact
import org.jetbrains.uast.analysis.UNullability
import org.jetbrains.uast.analysis.UastAnalysisPlugin
import org.jetbrains.uast.kotlin.analysis.KotlinUastAnalysisPlugin
import org.jetbrains.uast.visitor.AbstractUastVisitor
import kotlin.test.assertTrue as kAssertTrue
import kotlin.test.fail as kFail

class KotlinUastAnalysisPluginTest : KotlinLightCodeInsightFixtureTestCase() {
    fun `test dataflow not null`() = doTest("""
        fun dataFlowNotNull(b: Boolean) {
            val a = if (b) null else "hello"
        
            if (a != null) {
                println(/*NOT_NULL*/a)
            }
        }
    """.trimIndent())

    fun `test not null declaration`() = doTest("""
        fun notNullDeclaration() {
            val a = "hello"
            println(/*NOT_NULL*/a)
        }
    """.trimIndent())

    fun `test nullable declaration`() = doTest("""
        fun nullableDeclaration(b: Boolean) {
            val a = if (b) null else "hello"
            println(/*NULLABLE*/ a)
        }
    """.trimIndent())

    fun `test platform types`() = doTest("""
        fun platformTypes() {
            println(/*UNKNOWN*/ java.lang.StringBuilder().append("a"))
        }
    """.trimIndent())

    fun `test platform types1`() = doTest(
        """
        fun platformTypes() {
            val append = java.lang.StringBuilder().append("a")
 println(/*UNKNOWN*/ append)
        }
    """.trimIndent())

    fun `test kotlin type not null`() = doTest("""
        fun kotlinTypeNotNull(builder: kotlin.text.StringBuilder) {
            println(/*NOT_NULL*/builder)
        }
    """.trimIndent())

    fun `test kotlin type nullable`() = doTest("""
        fun kotlinTypeNullable(builder: kotlin.text.StringBuilder?) {
            println(/*NULLABLE*/builder)
        }
    """.trimIndent())

    fun `test elvis nullability`() = doTest("""
        fun elvis(b: Boolean) {
            val a = if (b) null else "hello"
            println(/*NOT_NULL*/ (a ?: return))
        }
    """.trimIndent())

    fun `test null expression`() = doTest("""
        fun nullExpression() {
            println(/*NULLABLE*/ null)
        }
    """.trimIndent())

    fun `test nullability of parameter with dfa`() = doTest("""
        fun nullableParamWithDfa(p: Int?) {
            if (p != null) {
                println(/*NOT_NULL*/p)
            }
        }
    """.trimIndent())

    fun `test nullability of if expression`() = doTest("""
        fun notNullIfExpression(d: Int?): Int = run {
            /*NOT_NULL*/ if (d != null) {
                return@run d
            } else {
                1
            }
        }
    """.trimIndent())

    fun `test nullability with platform type and if`() = doTest("""
        fun platformWithIf(): String = run {
            val a = java.lang.StringBuilder().append("a").toString()
            if (a != null) {
                return@run /*NOT_NULL*/ a
            } else {
                "a"
            }
        }
    """.trimIndent())

    fun `test if and elvis`() = doTest("""
        fun notNullIfWIthElvis(a: String?): String = run {
            if (a != null) {
                return@run /*NOT_NULL*/ a
            } else {
                return@run /*NOT_NULL*/ (a ?: "a")
            }
        }
    """.trimIndent())

    fun `test complex if condition`() = doTest("""
        fun twoNotNull(a: String?, b: String?) {
            if (a != null && b != null) {
                println(/*NOT_NULL*/a)
            }
        }
    """.trimIndent())

    fun `test class val property nullability`() = doTest("""
        class SomeClass {
            private val a: String? = if (kotlin.random.Random.nextBoolean()) null else "a"
            private fun notNullIfWithElvisStrangeType() = java.util.stream.Stream.of(1, 2).map {
                if (a != null) {
                    return@map (/*NOT_NULL*/ a)
                } else {
                    return@map (/*NULL*/ a)
                }
            }
        }
    """.trimIndent())

    fun `test class var property nullability`() = doTest("""
        class SomeClass {
            var a: String? = if (kotlin.random.Random.nextBoolean()) null else "a"
            private fun notNullIfWithElvisStrangeType() = java.util.stream.Stream.of(1, 2).map {
                if (a != null) {
                    return@map (/*NOT_NULL*/ a)
                } else {
                    return@map (/*NULL*/ a)
                }
            }
        }
    """.trimIndent())

    fun `test another class var property nullability`() = doTest("""
        class Other {
          var a: String? = if (kotlin.random.Random.nextBoolean()) null else "a"  
        }
        class SomeClass(val other: Other) {
            private fun notNullIfWithElvisStrangeType() = java.util.stream.Stream.of(1, 2).map {
                if (other.a != null) {
                    return@map (/*NULLABLE*/ other.a)
                } else {
                    return@map (/*NULLABLE*/ other.a)
                }
            }
        }
    """.trimIndent())

    fun `test simple property nullability`() = doTest("""
        class A(val /*NOT_NULL*/ d: String) {
          val /*NOT_NULL*/ a: String = ""
          val /*NULLABLE*/ b: String? = null
          val /*NOT_NULL*/ c: String
          
          init {
            c = ""
          }
        } 
    """.trimIndent())

    fun `test property nullability with generic`() = doTest("""
        class A<T>(a: T) {
          val /*NOT_NULL*/ b: T = a
          var /*NULLABLE*/ c: T? = a
          val /*NOT_NULL*/ d: List<T> = listOf(a)
        }
    """.trimIndent())

    fun `test data class value parameters nullability`() = doTest("""
        data class A(
          val /*NOT_NULL*/ a: String = "",
          var /*NULLABLE*/ b: String? = null,
        )
    """.trimIndent())

    fun `_test unsupported platform property type`() = doTest("""
        class A {
          val /*UNKNOWN*/ a: StringBuilder = java.lang.StringBuilder().append("a")
        }
    """.trimIndent())

    fun `_test unsupported property nullability with smartcast`() = doTest("""
        class A {
          val /*NOT_NULL*/ a = ""
          var /*NULLABLE*/ b = if (a.isEmpty()) null else a
          val /*NOT_NULL*/ c = listOf(a, b)
        }
    """.trimIndent())

    fun `_test unsupported with generic and nullable Any`() = doTest("""
        class A<E : Any?>(val /*NULLABLE*/ b: E) {
           fun unsupported(e: E) {
            val a = e
            println(/*NULLABLE*/ a)
           } 
        }
    """.trimIndent())

    private fun doTest(
        @Language("kotlin") source: String
    ) {
        val uastAnalysisPlugin = UastLanguagePlugin.byLanguage(KotlinLanguage.INSTANCE)?.analysisPlugin
        kAssertTrue(uastAnalysisPlugin is KotlinUastAnalysisPlugin, "Cannot find analysis plugin for Kotlin")

        val file = myFixture.configureByText("file.kt", source).toUElement() ?: kFail("Cannot create UFile")

        var visitAny = false

        file.accept(object : AbstractUastVisitor() {
            override fun visitElement(node: UElement): Boolean {
                uastAnalysisPlugin.checkNullability(node)?.let { visitAny = it }
                return super.visitElement(node)
            }
        })

        kAssertTrue(visitAny, "Test do nothing")
    }

    private fun UastAnalysisPlugin.checkNullability(node: UElement): Boolean? {
        val expectedUNullability = node.comments.firstOrNull()?.toUNullability() ?: return null

        val actualUNullability = when (node) {
            is UExpression -> node.getExpressionFact(UExpressionFact.UNullabilityFact)
            is UField -> node.getDeclarationFact(UDeclarationFact.UNullabilityFact)
            else -> return null
        }

        assertEquals(
            "Failed for ${node.asRenderString()}",
            expectedUNullability,
            actualUNullability
        )
        return true
    }

    private fun UComment.toUNullability() =
        text.removePrefix("/*").removeSuffix("*/").trim().let { UNullability.valueOf(it) }
}
