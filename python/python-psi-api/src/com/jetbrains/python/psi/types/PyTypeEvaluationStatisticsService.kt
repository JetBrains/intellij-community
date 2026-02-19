// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus

/**
 * This service acts as a bridge to avoid a direct dependency from the python-psi-api module
 * on the statistics module.
 *
 * Remove this temporary service when PY-87348 is done.
 */
@ApiStatus.Internal
interface PyTypeEvaluationStatisticsService {
  companion object {
    @JvmStatic
    fun getInstance(): PyTypeEvaluationStatisticsService {
      return ApplicationManager.getApplication().getService(PyTypeEvaluationStatisticsService::class.java)
    }
  }

  fun logJBTypeEngineTime(durationMs: Long)

  fun logHybridTypeEngineTime(durationMs: Long)
}
