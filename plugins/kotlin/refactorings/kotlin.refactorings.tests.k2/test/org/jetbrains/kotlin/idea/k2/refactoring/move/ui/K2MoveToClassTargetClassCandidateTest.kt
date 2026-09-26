// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

@OptIn(KaAllowAnalysisOnEdt::class)
class K2MoveToClassTargetClassCandidateTest : KotlinLightCodeInsightFixtureTestCase() {
    fun `test no candidates for parameterless function`() {
        val candidates = collectCandidates("""
            package sample

            fun target<caret>() {}
        """)
        assertEquals("", render(candidates))
    }

    fun `test value parameter with source class`() {
        val candidates = collectCandidates("""
            package sample

            class Foo

            fun target<caret>(p: Foo) {}
        """)
        assertEquals("VALUE_PARAMETER p: Foo (sample.Foo)", render(candidates))
    }

    fun `test extension receiver with source class`() {
        val candidates = collectCandidates("""
            package sample

            class Foo

            fun Foo.target<caret>() {}
        """)
        assertEquals("EXTENSION_RECEIVER <extension receiver>: Foo (sample.Foo)", render(candidates))
    }

    fun `test named context parameter with source class`() {
        val candidates = collectCandidates("""
            package sample

            class Foo

            context(ctx: Foo)
            fun target<caret>() {}
        """)
        assertEquals("CONTEXT_PARAMETER ctx: Foo (sample.Foo)", render(candidates))
    }

    fun `test underscore-named context parameter`() {
        val candidates = collectCandidates("""
            package sample

            class Foo

            context(_: Foo)
            fun target<caret>() {}
        """)
        assertEquals("CONTEXT_PARAMETER _: Foo (sample.Foo)", render(candidates))
    }

    fun `test ordering receiver context value`() {
        val candidates = collectCandidates("""
            package sample

            class Recv
            class Ctx
            class Val

            context(c: Ctx)
            fun Recv.target<caret>(p: Val) {}
        """)
        assertEquals(
            """
                EXTENSION_RECEIVER <extension receiver>: Recv (sample.Recv)
                CONTEXT_PARAMETER c: Ctx (sample.Ctx)
                VALUE_PARAMETER p: Val (sample.Val)
            """.trimIndent(),
            render(candidates),
        )
    }

    fun `test stdlib value parameter types are filtered out`() {
        val candidates = collectCandidates("""
            package sample

            fun target<caret>(i: Int, s: String, u: Unit) {}
        """)
        assertEquals("", render(candidates))
    }

    fun `test stdlib extension receiver is filtered out`() {
        val candidates = collectCandidates("""
            package sample

            fun Int.target<caret>() {}
        """)
        assertEquals("", render(candidates))
    }

    fun `test stdlib context parameter is filtered out`() {
        val candidates = collectCandidates("""
            package sample

            context(s: String)
            fun target<caret>() {}
        """)
        assertEquals("", render(candidates))
    }

    fun `test mix of source and stdlib value parameters`() {
        val candidates = collectCandidates("""
            package sample

            class Foo
            class Bar

            fun target<caret>(a: Int, b: Foo, c: String, d: Bar) {}
        """)
        assertEquals(
            """
                VALUE_PARAMETER b: Foo (sample.Foo)
                VALUE_PARAMETER d: Bar (sample.Bar)
            """.trimIndent(),
            render(candidates),
        )
    }

    fun `test functional type is filtered out`() {
        val candidates = collectCandidates("""
            package sample

            fun target<caret>(p: (Int) -> Int) {}
        """)
        assertEquals("", render(candidates))
    }

    fun `test generic stdlib type is filtered out`() {
        val candidates = collectCandidates("""
            package sample

            class Foo

            fun target<caret>(p: List<Foo>) {}
        """)
        assertEquals("", render(candidates))
    }

    fun `test generic source class is included`() {
        val candidates = collectCandidates("""
            package sample

            class Box<T>

            fun target<caret>(p: Box<Int>) {}
        """)
        assertEquals("VALUE_PARAMETER p: Box<Int> (sample.Box)", render(candidates))
    }

    fun `test nullable source type`() {
        val candidates = collectCandidates("""
            package sample

            class Foo

            fun target<caret>(p: Foo?) {}
        """)
        assertEquals("", render(candidates))
    }

    fun `test type alias to source class`() {
        val candidates = collectCandidates("""
            package sample

            class Foo
            typealias FooAlias = Foo

            fun target<caret>(p: FooAlias) {}
        """)
        assertEquals("VALUE_PARAMETER p: FooAlias (sample.FooAlias)", render(candidates))
    }

    fun `test cross-file source class`() {
        myFixture.addFileToProject(
            "Foo.kt",
            /*language=kotlin*/ """
                package sample

                class Foo
            """.trimIndent(),
        )
        val candidates = collectCandidates("""
            package sample

            fun target<caret>(p: Foo) {}
        """)
        assertEquals("VALUE_PARAMETER p: Foo (sample.Foo)", render(candidates))
    }

    fun `test java source class is filtered out`() {
        myFixture.addFileToProject(
            "JavaFoo.java",
            """
                package sample;

                public class JavaFoo {}
            """.trimIndent(),
        )
        val candidates = collectCandidates("""
            package sample

            fun target<caret>(p: JavaFoo) {}
        """)
        assertEquals("", render(candidates))
    }

    fun `test same source type as receiver and value parameter`() {
        val candidates = collectCandidates("""
            package sample

            class Foo

            fun Foo.target<caret>(p: Foo) {}
        """)
        assertEquals(
            """
                EXTENSION_RECEIVER <extension receiver>: Foo (sample.Foo)
                VALUE_PARAMETER p: Foo (sample.Foo)
            """.trimIndent(),
            render(candidates),
        )
    }

    fun `test type parameters are filtered out`() {
        val candidates = collectCandidates("""
            package sample

            fun <T> target<caret>(p: T) {}
        """)
        assertEquals("", render(candidates))
    }

    fun `test pointer element kinds`() {
        val candidates = collectCandidates("""
            package sample

            class Recv
            class Ctx
            class Val

            context(c: Ctx)
            fun Recv.target<caret>(p: Val) {}
        """)
        assertEquals(
            """
                EXTENSION_RECEIVER <extension receiver>: Recv (sample.Recv)
                CONTEXT_PARAMETER c: Ctx (sample.Ctx)
                VALUE_PARAMETER p: Val (sample.Val)
            """.trimIndent(),
            render(candidates),
        )
    }

    fun `test class function`() {
        val candidates = collectCandidates("""
            package sample

            class Ctx
            class Val
            class Rcv

            class Bar {
                context(c: Ctx)
                fun Rcv.target<caret>(v: Val) {}
            }
        """)
        assertEquals(
            """
                EXTENSION_RECEIVER <extension receiver>: Rcv (sample.Rcv)
                CONTEXT_PARAMETER c: Ctx (sample.Ctx)
                VALUE_PARAMETER v: Val (sample.Val)
            """.trimIndent(),
            render(candidates),
        )
    }

    fun `test interface function`() {
        val candidates = collectCandidates("""
            package sample

            class Ctx
            class Val
            class Rcv

            interface Bar {
                context(c: Ctx)
                fun Rcv.target<caret>(v: Val) {}
            }
        """)
        assertEquals(
            """
                EXTENSION_RECEIVER <extension receiver>: Rcv (sample.Rcv)
                CONTEXT_PARAMETER c: Ctx (sample.Ctx)
                VALUE_PARAMETER v: Val (sample.Val)
            """.trimIndent(),
            render(candidates),
        )
    }

    fun `test enum class function`() {
        val candidates = collectCandidates("""
            package sample

            class Ctx
            class Val
            class Rcv

            enum class Bar {
                A, B;
            
                context(c: Ctx)
                fun Rcv.target<caret>(v: Val) {}
            }
        """)
        assertEquals(
            """
                EXTENSION_RECEIVER <extension receiver>: Rcv (sample.Rcv)
                CONTEXT_PARAMETER c: Ctx (sample.Ctx)
                VALUE_PARAMETER v: Val (sample.Val)
            """.trimIndent(),
            render(candidates),
        )
    }

    fun `test exclude the same class`() {
        val candidates = collectCandidates("""
            package sample

            class Cls {
                fun target<caret>(param: Cls) {}
            }
        """)
        assertEquals("", render(candidates))
    }

    fun `test keep nested and inner classes`() {
        val candidates = collectCandidates("""
            package sample

            class Cls {
                class Nested
                inner class Inner
            
                fun target<caret>(n: Nested, i: Inner) {}
            }
        """)
        assertEquals("""
            VALUE_PARAMETER n: Nested (sample.Cls.Nested)
            VALUE_PARAMETER i: Inner (sample.Cls.Inner)
        """.trimIndent(), render(candidates))
    }

    private fun collectCandidates(@Language("kotlin") source: String): List<TargetClassCandidateParameter> {
        myFixture.configureByText(KotlinFileType.INSTANCE, source)
        val function = myFixture.elementAtCaret.getParentOfType<KtFunction>(strict = false)
            ?: error("No function at caret")
        return allowAnalysisOnEdt { findTargetClassCandidates(function) }
    }

    private fun render(candidates: List<TargetClassCandidateParameter>): String =
        candidates.joinToString(System.lineSeparator(), transform = ::renderCandidate)

    private fun renderCandidate(c: TargetClassCandidateParameter): String {
        return "${c.kind} ${c.displayName}: ${c.typeText} (${c.targetClassFqName.asString()})"
    }
}
