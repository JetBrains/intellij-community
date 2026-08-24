// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.python.pytools.PackageManagerPyTool
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.ToolCommandSpec

/**
 * Detection specs for every package-manager [PyTool] (uv, poetry, pipenv, hatch, conda), collected from
 * the [PyTool] extension point rather than hardcoded — a new package manager is picked up automatically.
 */
internal val packageManagerToolCommandSpecs: List<ToolCommandSpec>
  get() = PyTool.EP_NAME.extensionList.filter { it is PackageManagerPyTool }.map { it.toolCommandSpec }
