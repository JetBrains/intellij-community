// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.annotations.ApiStatus

/**
 * Feature flags for the new Python Interpreter / Workspace Structure settings pages.
 *
 * Each flag maps to a registry key declared in `intellij.pycharm.community.ide.impl.xml`;
 * flipping the key swaps the behavior at runtime without a code change.
 */
@ApiStatus.Internal
object PyInterpreterSettingsFlags {
  /**
   * The new "All Interpreters" and "Workspace / Project Structure" pages replace the legacy
   * "Python | Interpreter" and "Project Structure" pages. On by default; flip the key off to
   * fall back to the pre-PY-89840 layout.
   */
  @JvmStatic
  fun areNewInterpreterPagesEnabled(): Boolean = RegistryManager.getInstance().`is`("python.interpreter.settings.redesign")

  /**
   * The "Auto-configure project workspace from pyproject.toml" checkbox + its cleanup flow on
   * the Workspace Structure page. On by default; flip to `false` to hide the toggle during a
   * rollback window.
   */
  @JvmStatic
  fun isAutoConfigureToggleEnabled(): Boolean = RegistryManager.getInstance().`is`("python.pyproject.toggle.in.settings")
}
