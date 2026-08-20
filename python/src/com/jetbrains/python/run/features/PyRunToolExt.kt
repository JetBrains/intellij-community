// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.features

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.features.PyRunToolProvider.Companion.forSdk

internal fun AbstractPythonRunConfiguration<*>.useRunTool(sdk: Sdk): Boolean {
  val explicit = this.useRunTool
  if (explicit != null) return explicit
  val provider = forSdk(sdk) ?: return false
  return provider.initialToolState
}

/**
 * The tool this configuration will actually be run through, or `null` when it will be run by the interpreter itself.
 * Backs everything the run configuration says about that tool, so the name and the icon cannot disagree.
 */
internal fun AbstractPythonRunConfiguration<*>.activeRunToolData(): PyRunToolData? {
  if (!enableRunTool) return null
  val sdk = sdk ?: return null
  val provider = forSdk(sdk) ?: return null
  return if (useRunTool(sdk)) provider.runToolData else null
}