package com.intellij.lambda.testFramework.junit

import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.util.containers.orNull
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class StartIdeBeforeEachCallback : BeforeEachCallback {
  override fun beforeEach(context: ExtensionContext) {
    val modeFilter = context.getConfigurationParameter("ide.run.mode.filter").orNull()
    if (modeFilter != null) {
      IdeInstance.startIde(IdeRunMode.valueOf(modeFilter))
    }
  }
}