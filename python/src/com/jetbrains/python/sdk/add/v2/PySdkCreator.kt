// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject

interface PySdkCreator {
  /**
   * Error is shown to user. Do not catch all exceptions, only return exceptions valuable to user
   */
  suspend fun getSdk(moduleOrProject: ModuleOrProject): PyResult<Pair<Sdk, InterpreterStatisticsInfo>>

  /**
   * Creates the Python module structure using tools (uv, poetry, hatch, etc) within the given project module.
   *
   * Examples of possible python module structures:
   *
   * [com.jetbrains.python.hatch]
   * my-project
   *  ├── src
   *  │   └── my_project
   *  │       ├── __about__.py
   *  │       └── __init__.py
   *  ├── tests
   *  │   └── __init__.py
   *  ├── LICENSE.txt
   *  ├── README.md
   *  └── pyproject.toml
   *
   * [com.jetbrains.python.sdk.uv]
   * my-project
   *  ├── main.py
   *  ├── README.md
   *  └── pyproject.toml
   *
   * [com.jetbrains.python.poetry]
   * my-project
   *  ├── my_project
   *  │       └── __init__.py
   *  ├── tests
   *  │   └── __init__.py
   *  ├── README.md
   *  └── pyproject.toml
   */
  suspend fun createPythonModuleStructure(module: Module): PyResult<Unit> = Result.success(Unit)

}