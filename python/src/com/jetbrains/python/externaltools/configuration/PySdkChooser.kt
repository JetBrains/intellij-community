// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.externaltools.configuration

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk

const val DEFAULT_ENVIRONMENT: String = "Project Default"

fun Project.sdkByNameDefaultAware(name: String): Sdk? =
  if (name == DEFAULT_ENVIRONMENT) pythonSdk ?: modules.firstOrNull()?.pythonSdk else PythonSdkUtil.findSdkByKey(name)
