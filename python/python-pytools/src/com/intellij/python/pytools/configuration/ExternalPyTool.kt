// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.configuration

import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project

/**
 * Marks a [com.intellij.python.pytools.PyTool] as one that is **listed on the External Tools settings
 * page**. Presence of this interface is what makes a tool appear (and be searchable) there. It also
 * contributes the tool's detail panel — the feature toggles shown inline when the tool's row is
 * expanded. Kept separate from `PyTool` so a tool opts into the page without every tool having to.
 */
interface ExternalPyTool {
  /** The inline detail configurable (feature toggles) embedded in the tool's expanded row. */
  fun createConfigurable(project: Project): UnnamedConfigurable
}
