/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.BaseFixture

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
}
