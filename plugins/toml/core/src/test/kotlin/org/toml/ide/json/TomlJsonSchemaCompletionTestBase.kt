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
}
