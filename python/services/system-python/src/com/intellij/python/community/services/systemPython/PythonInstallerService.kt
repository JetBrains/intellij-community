// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython

import com.jetbrains.python.Result
import org.jetbrains.annotations.ApiStatus

/**
 * Tool to install python on OS.
 */
@ApiStatus.NonExtendable
sealed interface PythonInstallerService {

  /**
   * Installs latest stable python on OS.
   * Returns Unit for now (so you should call [SystemPythonService.findSystemPythons]), but this is a subject to change.
   */
  @ApiStatus.Experimental
  suspend fun installLatestPython(): Result<Unit, String>
}