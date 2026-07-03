// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.configuration

import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project

/**
 * Implemented by a [com.intellij.python.pytools.PyTool] that has settings on the External Tools page:
 * it contributes the detail panel shown in the table's Edit dialog. Presence of this interface is what
 * marks a tool as configurable (and therefore visible) on that page. Kept separate from `PyTool` so a
 * tool opts into a settings UI without every tool having to.
 */
interface ConfigurablePyTool {
  fun createConfigurable(project: Project): UnnamedConfigurable
}
