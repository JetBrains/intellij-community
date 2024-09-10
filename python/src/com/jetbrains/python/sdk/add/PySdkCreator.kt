// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject

interface PySdkCreator {
  @RequiresEdt
  fun getSdk(moduleOrProject: ModuleOrProject): Sdk

  @RequiresEdt
  fun createStatisticsInfo(): InterpreterStatisticsInfo? = null
}