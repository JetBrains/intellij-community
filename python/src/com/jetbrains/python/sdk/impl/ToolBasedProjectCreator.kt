// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyproject.model.spi.PyProjectCreator
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.toEelFileSystem
import com.jetbrains.python.venvReader.Directory

internal class ToolBasedProjectCreator(
  private val createRuntime: suspend (EelFileSystem, where: Directory) -> Result<PyToolRuntime, PyError>,
  private val createProject: suspend (name: @NlsSafe String, runtime: PyToolRuntime) -> PyResult<*>,
) : PyProjectCreator {
  override suspend fun createProject(
    where: Directory,
    name: @NlsSafe String,
  ): PyResult<Unit> {
    val runtime = createRuntime(where.toEelFileSystem(), where).getOr { return it }
    return createProject(name, runtime.withWorkingDirectory(where)).mapSuccess { }
  }
}
