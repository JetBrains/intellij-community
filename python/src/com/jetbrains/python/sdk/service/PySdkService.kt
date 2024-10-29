// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.service

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Temporary service to manage python sdks from the one spot.
 * Use [Project.pySdkService]
 */
@Service(Service.Level.PROJECT)
@Internal
class PySdkService private constructor(private val project: Project) {
  companion object {
    val Project.pySdkService: PySdkService get() = service<PySdkService>()
  }

  private val mutex = Mutex()
  private val model get() = PyConfigurableInterpreterList.getInstance(project).model

  /**
   * Persists SDK. Call is thread and SDK existence agnostic
   */
  suspend fun persistSdk(sdk: Sdk): Unit = coroutineScope {
    mutex.withLock {
      writeAction {
        if (!sdkExists(sdk)) {
          with(model) {
            addSdk(sdk)
            apply()
          }
        }
      }
    }
    ensureSdkSaved(sdk) // As an assert
  }

  /**
   * Throws error if SDK isn't persistent (used as assertion only)
   */
  fun ensureSdkSaved(sdk: Sdk) {
    if (!sdkExists(sdk)) {
      error("There is no $sdk")
    }
  }

  private fun sdkExists(sdk: Sdk): Boolean = model.findSdk(sdk.name) != null
}

