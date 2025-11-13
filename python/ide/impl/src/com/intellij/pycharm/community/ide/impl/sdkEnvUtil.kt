// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.asBinToExecute
import com.jetbrains.python.sdk.configuration.EnvCheckerResult

internal suspend fun PythonBinary.findEnvOrNull(@IntentionName intentionName: String): EnvCheckerResult.EnvFound? =
  validatePythonAndGetInfo().findEnvOrNull(intentionName)

internal suspend fun Sdk.findEnvOrNull(@IntentionName intentionName: String): EnvCheckerResult.EnvFound? =
  asBinToExecute().validatePythonAndGetInfo().findEnvOrNull(intentionName)

internal fun PyResult<PythonInfo>.findEnvOrNull(@IntentionName intentionName: String): EnvCheckerResult.EnvFound? {
  return orLogException(logger)?.let { EnvCheckerResult.EnvFound(it, intentionName) }
}
private val logger = fileLogger()
