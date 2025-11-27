package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.coroutineScopesCancellationTimeout
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.time.Duration.Companion.milliseconds

open class ConfigureCoroutineCancellationTimeout : BeforeAllCallback, BeforeEachCallback {
  companion object {
    fun configure() {
      ConfigurationStorage.coroutineScopesCancellationTimeout = 500.milliseconds
    }
  }

  override fun beforeEach(context: ExtensionContext) = configure()

  override fun beforeAll(context: ExtensionContext) = configure()
}