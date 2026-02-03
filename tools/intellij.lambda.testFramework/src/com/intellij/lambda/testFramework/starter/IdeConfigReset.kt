package com.intellij.lambda.testFramework.starter

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class IdeConfigReset : AfterAllCallback {
  override fun afterAll(context: ExtensionContext) {
    IdeStartConfig.current = IdeStartConfig.default
  }
}