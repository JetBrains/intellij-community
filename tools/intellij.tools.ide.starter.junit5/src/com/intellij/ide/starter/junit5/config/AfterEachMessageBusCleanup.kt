package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.afterEachMessageBusCleanup
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Enables cleanup of the message bus after each test.
 * Might be useful your tests don't share any resources.
 */
open class AfterEachMessageBusCleanup : BeforeAllCallback, BeforeEachCallback {
  private fun configure() {
    ConfigurationStorage.afterEachMessageBusCleanup(true)
  }

  override fun beforeEach(context: ExtensionContext) = configure()

  override fun beforeAll(context: ExtensionContext?) = configure()
}

