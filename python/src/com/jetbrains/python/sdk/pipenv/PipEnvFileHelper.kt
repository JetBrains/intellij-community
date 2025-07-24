// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.findAmongRoots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object PipEnvFileHelper {
  const val PIP_FILE: String = "Pipfile"
  const val PIP_FILE_LOCK: String = "Pipfile.lock"
  const val PIPENV_PATH_SETTING: String = "PyCharm.Pipenv.Path"

  fun getPipFileLock(sdk: Sdk): VirtualFile? =
    sdk.associatedModulePath?.let { StandardFileSystems.local().findFileByPath(it)?.findChild(PIP_FILE_LOCK) }

  suspend fun pipFile(module: Module): VirtualFile? = withContext(Dispatchers.IO) { findAmongRoots(module, PIP_FILE) }
}