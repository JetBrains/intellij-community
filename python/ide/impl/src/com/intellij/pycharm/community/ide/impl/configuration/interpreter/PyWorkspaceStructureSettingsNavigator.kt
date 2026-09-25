// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.options.ShowSettingsUtil
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.inspections.PythonInterpreterSettingsNavigator

/**
 * Routes the [com.jetbrains.python.sdk.inspections.InterpreterSettingsQuickFix] and the interpreter
 * status-bar widget's "Interpreter Settings…" action to the redesigned "Workspace Structure" page
 * (PY-89840). Enabled by the [PyInterpreterRedesignFlags] gate — when the flag is off the navigator
 * opts out and the QuickFix falls back to the legacy configurable.
 *
 * Registered as an `applicationService` so the lower `intellij.python.community.impl` module can
 * look it up without a back-dependency on this module.
 */
internal class PyWorkspaceStructureSettingsNavigator : PythonInterpreterSettingsNavigator {

  override fun tryNavigate(target: ModuleOrProject): Boolean {
    if (!PyInterpreterRedesignFlags.isEnabled()) return false
    ShowSettingsUtil.getInstance().showSettingsDialog(target.project, PyWorkspaceStructureConfigurable::class.java)
    return true
  }
}
