// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.textmate

import com.intellij.ide.AppLifecycleListener

internal class TextMateAppLifecycleListener : AppLifecycleListener {
  override fun appFrameCreated(commandLineArgs: List<String>) {
    TextMateService.getInstance().startInitialization()
  }
}
