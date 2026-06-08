// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.lang.Language
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.injection.KotlinInjectionTestBase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.test.util.invalidateCaches

class K2KotlinInjectionTest: KotlinInjectionTestBase() {

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    fun testKotlinReplaceWithBuildInsInjection() {
        myFixture.configureByText(
            "a.kt",
            """
            @kotlin.Deprecated("it's outdated", kotlin.ReplaceWith("do<caret>Fun(42)"))
            fun bar() {}
            """.trimIndent()
        )

        assertFalse(
            "Injection action is available. There's probably no injection at caret place",
            isInjectLanguageActionAvailable()
        )

        assertEquals("Wrong injection language", KotlinLanguage.INSTANCE, injectedFile?.language)
    }

    fun testInjectionIntoMultiDollarString() {
        val file = myFixture.configureByText(
            "a.kt",
            """
                // COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
                
                import org.intellij.lang.annotations.Language
                
                @Language("JSON")
                val json = $$$""${'"'}
                {
                  "openapi": "3.0.2",
                  "info": {
                    "title": "REST",
                    "version": "1.0.0"
                  }
                }
                ""${'"'}
            """.trimIndent()
        )

        withCustomCompilerOptions(file.text, project, module) {
            myFixture.checkHighlighting()
        }
    }

    fun testInjectKotlinInJava() {
        myFixture.configureByText(
            "a.java",
            """
                
                import org.intellij.lang.annotations.Language;
                
                class Main {
                  {
                     @Language("kotlin")
                     var kotlinCode = "package foo; class A"; 
                  }
                }
            """.trimIndent()
        )

        myFixture.checkHighlighting()
    }

    fun testInjectKotlinInKDocCodeBlock() {
        assertKDocInjectionPresent(
            """
            /**
             * ```
             * val foo = 42<caret>
             * println(foo)
             * ```
             */
            fun usage() {}
            """,
            KotlinLanguage.INSTANCE
        )
    }

    fun testInjectKotlinInKDocCodeBlockWithTildes() {
        assertKDocInjectionPresent(
            """
            /**
             * ~~~~
             * val foo = 42<caret>
             * println(foo)
             * ~~~~
             */
            fun usage() {}
            """,
            KotlinLanguage.INSTANCE
        )
    }

    fun testInjectKotlinInIndentation() {
        assertKDocInjectionPresent(
            """
            /**
             * Regular text
             *
             *    val foo = 42<caret>
             *    println(foo)
             *
             * some text
             */
            fun usage() {}
            """,
            KotlinLanguage.INSTANCE
        )
    }

    fun testInjectKotlinInCodeSpanText() {
        assertKDocInjectionPresent(
            """
            /**
             * ````fun fo<caret>o(){}````
             */
            fun usage() {}
            """,
            KotlinLanguage.INSTANCE
        )
    }

    fun testInjectKotlinInKDocCodeSpan() {
        assertKDocInjectionPresent(
            """
            /**
             * Inline: `prin<caret>tln(42)`
             */
            fun usage() {}
            """,
            KotlinLanguage.INSTANCE
        )
    }

    private fun assertKDocInjectionPresent(text: String, language: Language) {
        myFixture.configureByText("a.kt", text.trimIndent())

        myFixture.checkHighlighting()

        val expectedLanguage = myInjectionFixture.getAllInjections().mapTo(hashSetOf()) { it.second.language }.singleOrNull()
        assertEquals("Wrong injection language", language, expectedLanguage)
    }
}
