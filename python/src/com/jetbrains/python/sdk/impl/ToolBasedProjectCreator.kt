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

/**
 * Adaptor for [PyProjectCreator] for managers that use [PyToolRuntime].
 * Configure it with [PyToolFuns]
 */
internal class ToolBasedProjectCreator(
  private val funs: PyToolFuns,
) : PyProjectCreator {

  override suspend fun createProject(
    where: Directory,
    name: @NlsSafe String?,
  ): PyResult<Unit> {
    val runtime = funs.createRuntime(where.toEelFileSystem(), where).getOr { return it }
    return funs.createProject(name, runtime.withWorkingDirectory(where), where).mapSuccess { }
  }

  internal interface PyToolFuns {
    suspend fun createRuntime(fs: EelFileSystem, where: Directory): Result<PyToolRuntime, PyError>

    /**
     * See [PyProjectCreator.createProject] for semantics.
     * [runtime] was created by [createRuntime]
     */
    suspend fun createProject(name: @NlsSafe String?, runtime: PyToolRuntime, where: Directory): PyResult<*>
  }
}
