// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirementsTxt

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

/** Writes the packages that the code of a module imports into its requirements file. */
@ApiStatus.Internal
interface PyRequirementsSyncService {
  /** Adds the imported packages of [module] to its requirements file, and notifies the user of the result. */
  suspend fun syncWithImports(module: Module)

  companion object {
    fun getInstance(): PyRequirementsSyncService = service()
  }
}