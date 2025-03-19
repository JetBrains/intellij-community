// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.toolWindow.DefaultToolWindowLayoutBuilder
import com.intellij.toolWindow.DefaultToolWindowLayoutExtension

class PyToolWindowLayoutProvider : DefaultToolWindowLayoutExtension {
  override fun buildV1Layout(builder: DefaultToolWindowLayoutBuilder) {
  }

  override fun buildV2Layout(builder: DefaultToolWindowLayoutBuilder) {
    builder.right.addOrUpdate("Plots") { weight = 0.1f }
    builder.bottom.addOrUpdate("Python Packages") { weight = 0.1f }
    builder.bottom.addOrUpdate("Python Console") { weight = 0.1f }
    thisLogger().info("Python default layout applied")
  }
}