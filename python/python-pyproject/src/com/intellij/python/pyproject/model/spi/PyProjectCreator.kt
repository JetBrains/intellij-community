// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.spi

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.venvReader.Directory
import org.jetbrains.annotations.CheckReturnValue

/**
 * Tool to create new projects (i.e. `uv init`)
 */
fun interface PyProjectCreator {
  /**
   * Create project named [name] on [where].
   * Error will be displayed to user, [where] will be refreshed in case of success
   */
  @CheckReturnValue
  suspend fun createProject(where: Directory, name: @NlsSafe String): PyResult<Unit>
}