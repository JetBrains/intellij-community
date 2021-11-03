/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.json

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase
import org.toml.ide.completion.TomlCompletionFixture
import org.toml.ide.experiments.TomlExperiments

abstract class TomlJsonSchemaCompletionTestBase : TomlTestBase() {
    lateinit var completionFixture: TomlCompletionFixture

    override fun setUp() {
        super.setUp()
        completionFixture = TomlCompletionFixture(myFixture, "Cargo.toml")
        completionFixture.setUp()
        JsonSchemaProviderFactory.EP_NAME.point.registerExtension(TomlJsonSchemaProviderFactory(), testRootDisposable)

        // Enable experimental feature, but restore the initial state after test
        val initialRegistryValue = Registry.get(TomlExperiments.JSON_SCHEMA).asBoolean()
        Registry.get(TomlExperiments.JSON_SCHEMA).setValue(true)
        Disposer.register(testRootDisposable) {
            Registry.get(TomlExperiments.JSON_SCHEMA).setValue(initialRegistryValue)
        }
    }

    fun checkContainsCompletion(
        variants: List<String>,
        @Language("TOML") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) = completionFixture.checkContainsCompletion(variants, code, render)
}
