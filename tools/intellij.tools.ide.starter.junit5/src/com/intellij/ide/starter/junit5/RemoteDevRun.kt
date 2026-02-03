package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.splitMode
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

open class RemoteDevRun : BeforeAllCallback, BeforeEachCallback {
  private fun configure() {
    ConfigurationStorage.splitMode(true)
  }

  override fun beforeAll(context: ExtensionContext) {
    configure()
  }

  override fun beforeEach(context: ExtensionContext) {
    configure()
  }
}