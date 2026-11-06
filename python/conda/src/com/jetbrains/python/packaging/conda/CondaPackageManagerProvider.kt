// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class CondaPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManager(project: Project, interpreter: PythonInterpreter): PythonPackageManager? =
    if (interpreter.isCondaVirtualEnv) CondaPackageManager(project, interpreter) else null
}
