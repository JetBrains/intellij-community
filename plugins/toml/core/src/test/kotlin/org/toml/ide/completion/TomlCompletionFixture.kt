/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.BaseFixture
import org.junit.Assert

class TomlCompletionFixture(
    private val myFixture: CodeInsightTestFixture,
    private val defaultFileName: String = "example.toml"
) : BaseFixture() {

    fun checkContainsCompletion(
        variants: Set<String>,
        code: String,
        render: LookupElement.() -> String
    ) = withNoAutoCompletion {
        myFixture.configureByText(defaultFileName, code.trimIndent())
        doContainsCompletion(variants, render)
    }

    fun checkNotContainsCompletion(
        variants: Set<String>,
        code: String,
        render: LookupElement.() -> String
    ) = withNoAutoCompletion {
        myFixture.configureByText(defaultFileName, code.trimIndent())
        doNotContainsCompletion(variants, render)
    }

    fun checkCompletionList(
        variants: List<String>,
        code: String,
        render: LookupElement.() -> String
    ) = withNoAutoCompletion {
        myFixture.configureByText(defaultFileName, code.trimIndent())
        val lookups = myFixture.completeBasic()

        checkNotNull(lookups) {
            "Expected completions that contain $variants, but no completions found"
        }

        val renderedLookups = lookups.map { it.render() }
        Assert.assertEquals(variants, renderedLookups)
    }

    private fun withNoAutoCompletion(block: () -> Unit) {
        val prevSetting = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
        try {
            block()
        } finally {
            CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = prevSetting
        }
    }

    private fun doContainsCompletion(variants: Set<String>, render: LookupElement.() -> String) {
        val lookups = myFixture.completeBasic()

        checkNotNull(lookups) {
            "Expected completions that contain $variants, but no completions found"
        }

        val renderedLookups = lookups.map { it.render() }
        for (variant in variants) {
            if (variant !in renderedLookups) {
                error("Expected completions that contain $variant, but got ${lookups.map { it.render() }}")
            }
        }
    }

    private fun doNotContainsCompletion(variants: Set<String>, render: LookupElement.() -> String = { lookupString }) {
        val lookups = myFixture.completeBasic()

        checkNotNull(lookups) {
            "Expected completions that contain $variants, but no completions found"
        }

        if (lookups.any { it.render() in variants }) {
            error("Expected completions that don't contain $variants, but got ${lookups.map { it.render() }}")
        }
    }

    private fun executeSoloCompletion() {
        val lookups = myFixture.completeBasic()

        if (lookups != null) {
            if (lookups.size == 1) {
                // for cases like `frob/*caret*/nicate()`,
                // completion won't be selected automatically.
                myFixture.type('\n')
                return
            }
            fun LookupElement.debug(): String = "$lookupString ($psiElement)"
            error("Expected a single completion, but got ${lookups.size}\n"
                  + lookups.joinToString("\n") { it.debug() })
        }
    }

    fun doSingleCompletion(code: String, after: String) {
        myFixture.configureByText(defaultFileName, code.trimIndent())
        executeSoloCompletion()
        myFixture.checkResult(after.trimIndent())
    }
}
