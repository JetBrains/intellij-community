package com.intellij.lambda.sampleTestsWithFixtures.util

import com.intellij.lambda.testFramework.starter.IdeStartConfig
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class IdeConfigSetup : BeforeAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    IdeStartConfig.current = IdeStartConfig(
      configureTestContext = { applyVMOptionsPatch { inUnitTestMode() } }
    )
  }
}

