// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestUtil
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

abstract class KotlinAutoPopupTest {
    companion object {
        private val projectFixture = projectFixture(openAfterCreation = true)
    }

    protected val myFixture: CodeInsightTestFixture by codeInsightModuleFixture(projectFixture)

    private val lookup: LookupImpl? get() = CompletionAutoPopupTestUtil.getLookup(myFixture)

    private fun type(s: String) = CompletionAutoPopupTestUtil.type(myFixture, s)

    private fun runTest(block: () -> Unit) = CompletionAutoPopupTestUtil.runWithAutoPopupEnabled<Throwable>(myFixture, block)

    

    @Test
    fun testAfterLT(): Unit = runTest {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            fun test(x:Int) {
                if (x <caret>)
            }
        """.trimIndent())

        type("<")
        assertNull(lookup)
    }

    @Test
    fun testAfterAtInModifierList(): Unit = runTest {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            <caret>
            fun test() {}
        """.trimIndent())

        type("@")
        lookup?.items?.map { it.lookupString }.orEmpty().let { lookupStrings ->
            assertContains(lookupStrings, "receiver")
            assertContains(lookupStrings, "OptIn")
        }
        assertContains(lookup?.items?.map { it.lookupString }.orEmpty(), "OptIn")
    }

    @Test
    fun testAfterAtInFileAnnotationList(): Unit = runTest {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            <caret>
        """.trimIndent())

        type("@")
        lookup?.items?.map { it.lookupString }.orEmpty().let { lookupStrings ->
            assertContains(lookupStrings, "file")
            assertContains(lookupStrings, "OptIn")
        }
        type("file:")
        assertContains(lookup?.items?.map { it.lookupString }.orEmpty(), "OptIn")
    }

    @Test
    fun testPropertyKey(): Unit = runTest {
        myFixture.createFile("PropertyKey.properties", "foo.bar = 1")
        myFixture.createFile("PropertyKey.kt", """
            package org.jetbrains.annotations

            @Retention(AnnotationRetention.BINARY)
            @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FIELD)
            public annotation class PropertyKey(public val resourceBundle: String)

            fun message(@PropertyKey(resourceBundle = "PropertyKey") key: String) = key
        """.trimIndent())
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            import org.jetbrains.annotations.message

            fun test() {
                message("fo<caret>")
            }
        """.trimIndent())

        type("o")
        assertContains(lookup?.items?.map { it.lookupString }.orEmpty(), "foo.bar")
        type(".")
        assertContains(lookup?.items?.map { it.lookupString }.orEmpty(), "foo.bar")
    }

    @Test
    fun testNotPropertyKey(): Unit = runTest {
        myFixture.createFile("PropertyKey.properties", "foo.bar = 1")
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            fun test() {
                println("fo<caret>")
            }
        """.trimIndent())

        type("o")
        assertNull(lookup)
    }

    @Test
    fun testStringTemplateEntry(): Unit = runTest {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            class A {
                val foo = 1
            }

            fun test(a: A) {
                println("${"$"}a<caret>")
            }
        """.trimIndent())

        type(".")
        assertContains(lookup?.items?.map { it.lookupString }.orEmpty(), "foo")
    }
}