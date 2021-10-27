package org.toml.ide.json

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import org.toml.TomlTestBase
import org.toml.ide.experiments.TomlExperiments

abstract class TomlJsonSchemaTestBase : TomlTestBase() {
    override fun setUp() {
        super.setUp()

        // Register schema provider
        JsonSchemaProviderFactory.EP_NAME.point.registerExtension(TomlJsonSchemaProviderFactory(), testRootDisposable)

        // Enable experimental feature, but restore the initial state after test
        val initialRegistryValue = Registry.get(TomlExperiments.JSON_SCHEMA).asBoolean()
        Registry.get(TomlExperiments.JSON_SCHEMA).setValue(true)
        Disposer.register(testRootDisposable) {
            Registry.get(TomlExperiments.JSON_SCHEMA).setValue(initialRegistryValue)
        }
    }
}