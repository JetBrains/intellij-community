// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonProjectExt")

package com.jetbrains.python.extensions

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil

val Project.hasPython: Boolean
  get() = modules.asSequence()
    .map { PythonSdkUtil.findPythonSdk(it) }
    .any { it != null && it.sdkType is PythonSdkType }