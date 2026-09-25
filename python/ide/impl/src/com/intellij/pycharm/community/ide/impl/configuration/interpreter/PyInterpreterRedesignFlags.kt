// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * Single feature flag for the Python Interpreter settings redesign (PY-89840).
 *
 * `true` (default) — the redesigned "All Interpreters" and "Workspace / Project Structure" pages
 * replace the legacy "Python | Interpreter" and "Project Structure" pages. Flip the registry key
 * to `false` to fall back to the pre-redesign layout during rollback.
 */
@ApiStatus.Internal
object PyInterpreterRedesignFlags {
  @JvmStatic
  fun isEnabled(): Boolean = Registry.`is`("python.interpreter.settings.redesign", true)
}
