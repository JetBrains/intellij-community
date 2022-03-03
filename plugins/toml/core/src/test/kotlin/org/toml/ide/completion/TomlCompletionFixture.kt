/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.BaseFixture

class TomlCompletionFixture(
    private val myFixture: CodeInsightTestFixture,
    private val defaultFileName: String = "example.toml"
) : BaseFixture() {

    fun checkContainsCompletion(
        variants: List<String>,
        code: String,
        render: LookupElement.() -> String
    ) {
        myFixture.configureByText(defaultFileName, code.trimIndent())
        doContainsCompletion(variants, render)
    }

    private fun doContainsCompletion(variants: List<String>, render: LookupElement.() -> String) {
        val lookups = myFixture.completeBasic()

        checkNotNull(lookups) {
            "No completions found"
        }

        for (variant in variants) {
            if (lookups.all { it.render() != variant }) {
                error("Expected completions that contain $variant, but got ${lookups.map { it.render() }}")
            }
        }
    }
}
