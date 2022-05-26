/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.completion

import com.intellij.codeInsight.lookup.LookupElement
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase

abstract class TomlCompletionTestBase : TomlTestBase() {
    lateinit var completionFixture: TomlCompletionFixture

    override fun setUp() {
        super.setUp()
        completionFixture = TomlCompletionFixture(myFixture)
        completionFixture.setUp()
    }

    override fun tearDown() {
        completionFixture.tearDown()
        super.tearDown()
    }

    fun checkContainsCompletion(
        variants: Set<String>,
        @Language("TOML") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) = completionFixture.checkContainsCompletion(variants, code, render)
}

