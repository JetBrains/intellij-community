// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a Python binary that still has to be downloaded / installed before it can be used.
 * Call [install] to obtain a real [Sdk].
 * Held by [InstallableSelectableInterpreter] so the sealed interpreter
 * hierarchy can live in python-sdk without dragging in the installer module.
 */
@ApiStatus.NonExtendable
@ApiStatus.Internal
interface InstallablePythonSdk {
  // Name of the python to install
  val name: String

  /**
   * Install python
   */
  @RequiresEdt
  fun install(module: Module?, systemWideSdksDetector: () -> List<Sdk>): Result<Sdk>
}
