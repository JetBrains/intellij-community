// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalUtil

internal class GenOneTerminalOptionHidingActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (TerminalUtil.getGenOneTerminalVisibilityValue() == null) {
      val isGenOneTerminalEnabled = TerminalOptionsProvider.instance.terminalEngine == TerminalEngine.NEW_TERMINAL
      // Hide the New Terminal (Gen1) option if it is disabled on the first startup after the introduction of this change.
      TerminalUtil.setGenOneTerminalVisibilityValue(isGenOneTerminalEnabled)
    }
  }
}