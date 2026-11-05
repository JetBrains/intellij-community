// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pySdkAdditionalData
import org.jetbrains.annotations.ApiStatus

/** Whether this SDK is a Poetry environment. */
@get:ApiStatus.Internal
val Sdk.isPoetry: Boolean
  get() = PythonSdkUtil.isPythonSdk(this) && pySdkAdditionalData.flavor == PyPoetrySdkFlavor

/** Whether this interpreter is a Poetry environment. */
@get:ApiStatus.Internal
val PythonInterpreter.isPoetry: Boolean
  get() = getSdkAPI().isPoetry