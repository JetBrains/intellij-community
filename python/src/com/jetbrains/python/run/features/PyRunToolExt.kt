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