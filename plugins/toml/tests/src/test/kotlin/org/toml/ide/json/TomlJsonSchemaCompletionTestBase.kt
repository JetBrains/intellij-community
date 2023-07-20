/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.json

import com.intellij.codeInsight.lookup.LookupElement
import org.intellij.lang.annotations.Language
import org.toml.ide.completion.TomlCompletionFixture

abstract class TomlJsonSchemaCompletionTestBase : TomlJsonSchemaTestBase() {
    lateinit var completionFixture: TomlCompletionFixture

    override fun setUp() {
        super.setUp()
        completionFixture = TomlCompletionFixture(myFixture, "Cargo.toml")
        completionFixture.setUp()
    }

    fun checkContainsCompletion(
        variants: Set<String>,
        @Language("TOML") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) = completionFixture.checkContainsCompletion(variants, code, render)

    fun checkNotContainsCompletion(
        variants: Set<String>,
        @Language("TOML") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) = completionFixture.checkNotContainsCompletion(variants, code, render)

    fun doSingleCompletion(
        @Language("TOML") before: String,
        @Language("TOML") after: String
    ) = completionFixture.doSingleCompletion(before, after)

    /**
     * Compares all completion suggestions with [variants] taking order into account
     */
    fun checkCompletionList(
        variants: List<String>,
        @Language("TOML") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) = completionFixture.checkCompletionList(variants, code, render)
}
