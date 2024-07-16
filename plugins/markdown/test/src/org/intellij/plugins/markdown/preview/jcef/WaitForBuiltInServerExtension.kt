package org.intellij.plugins.markdown.preview.jcef

import org.jetbrains.ide.BuiltInServerManager
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

internal class WaitForBuiltInServerExtension: BeforeEachCallback {
  override fun beforeEach(context: ExtensionContext) {
    BuiltInServerManager.getInstance().waitForStart()
  }
}
