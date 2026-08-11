// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.textmate

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

internal class TextMateRequiredForSmartModeActivity : StartupActivity.RequiredForSmartMode {
  override fun runActivity(project: Project) {
    TextMateService.getInstance().ensureInitialized()
  }
}
