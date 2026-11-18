// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration.interpreter

import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.annotations.ApiStatus

/**
 * Feature flags of the interpreter settings pages. Each flag maps to a registry key declared in
 * `intellij.python.community.impl.xml`.
 */
@ApiStatus.Internal
object PyInterpreterSettingsFlags {
  /**
   * The "Auto-configure project workspace from pyproject.toml" checkbox + its cleanup flow on
   * the Workspace Structure page. On by default; flip to `false` to hide the toggle during a
   * rollback window.
   */
  @JvmStatic
  fun isAutoConfigureToggleEnabled(): Boolean = RegistryManager.getInstance().`is`("python.pyproject.toggle.in.settings")
}
