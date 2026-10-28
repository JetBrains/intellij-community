// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.filter.filterAssignablePythonSdks
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Returns all Python SDKs registered in the IDE that are usable from [ModuleOrProject.project], restricted to the target
 * the module resides on (only for [ModuleOrProject.ModuleAndProject]). Remote interpreters are sorted last, then by name.
 */
@Internal
fun ModuleOrProject.getAssignablePythonSdks(): List<Sdk> = filterAssignablePythonSdks(PythonSdkUtil.getAllSdks(this))

