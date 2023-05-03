// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ui

import com.intellij.toolWindow.DefaultToolWindowLayoutBuilder
import com.intellij.toolWindow.IntellijPlatformDefaultToolWindowLayoutProvider

open class PyToolWindowLayoutProvider : IntellijPlatformDefaultToolWindowLayoutProvider() {
  override fun configureBottomVisibleOnLargeStripe(builder: DefaultToolWindowLayoutBuilder) {
    super.configureBottomVisibleOnLargeStripe(builder)
    builder.add("Python Packages", 0.1f)
    builder.add("Python Console", 0.1f)
  }

  override fun configureRightVisibleOnLargeStripe(builder: DefaultToolWindowLayoutBuilder) {
    super.configureRightVisibleOnLargeStripe(builder)
    builder.add("SciView", 0.1f)
  }
}