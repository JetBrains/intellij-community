// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.orLogException

internal suspend fun PythonBinary.findEnvOrNull(@IntentionName intentionName: String): EnvCheckerResult.EnvFound? =
  validatePythonAndGetInfo().findEnvOrNull(intentionName)

internal fun PyResult<PythonInfo>.findEnvOrNull(@IntentionName intentionName: String): EnvCheckerResult.EnvFound? {
  return orLogException(logger)?.let { EnvCheckerResult.EnvFound(it, intentionName) }
}
private val logger = fileLogger()
