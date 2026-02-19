package com.intellij.lambda.testFramework.junit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.TestLoggerFactory
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class TestFactoryLoggerSetter : BeforeEachCallback, AfterEachCallback {
  private val factoryBefore: Logger.Factory = Logger.getFactory()

  override fun beforeEach(context: ExtensionContext) {
    Logger.setFactory(TestLoggerFactory::class.java)
  }

  override fun afterEach(context: ExtensionContext) {
    Logger.setFactory(factoryBefore)
  }
}
