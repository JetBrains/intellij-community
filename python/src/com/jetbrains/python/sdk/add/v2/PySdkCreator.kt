// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.Result
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.errorProcessing.PyError

interface PySdkCreator {
  /**
   * Error is shown to user. Do not catch all exceptions, only return exceptions valuable to user
   */
  suspend fun getSdk(moduleOrProject: ModuleOrProject): Result<Pair<Sdk, InterpreterStatisticsInfo>, PyError>
}