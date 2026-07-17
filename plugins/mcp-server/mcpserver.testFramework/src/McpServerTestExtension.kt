// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class McpServerTestExtension : BeforeAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    System.setProperty("java.awt.headless", "false")
  }
}
