package com.intellij.lambda.sampleTests.util

import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class SetLambdaPluginCallback : BeforeAllCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    LambdaTestPluginHolder.setupAdditionalLambdaPlugin("intellij.lambda.sampleTests._test",
                                                       "intellij.lambda.sampleTests.plugin",
                                                       "lambda-sampleTests-plugin")
  }

  override fun afterAll(context: ExtensionContext) {
    LambdaTestPluginHolder.cleanUpAdditionalLambdaPlugin()
  }
}