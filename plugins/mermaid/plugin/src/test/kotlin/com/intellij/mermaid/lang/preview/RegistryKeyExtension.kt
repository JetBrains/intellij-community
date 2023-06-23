package com.intellij.mermaid.lang.preview

import com.intellij.openapi.util.registry.Registry
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

internal open class RegistryKeyExtension(
  key: String,
  private val value: Boolean,
): BeforeAllCallback, AfterAllCallback {
  private val registryValue = Registry.get(key)
  private val previous = registryValue.asBoolean()

  override fun beforeAll(context: ExtensionContext?) {
    registryValue.setValue(value)
  }

  override fun afterAll(context: ExtensionContext?) {
    registryValue.setValue(previous)
  }
}
