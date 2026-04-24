// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.externaltools.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import org.jetbrains.annotations.ApiStatus

const val DEFAULT_ENVIRONMENT: String = "Project Default"

@ApiStatus.Experimental
fun Module.sdkByNameDefaultAware(name: String): Sdk? =
  if (name == DEFAULT_ENVIRONMENT) pythonSdk else PythonSdkUtil.findSdkByKey(name)
