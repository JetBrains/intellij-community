// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.requirements.PyDependenciesFile
import com.jetbrains.python.requirements.PyDependenciesFileProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class PipFileLockFileProvider : PyDependenciesFileProvider {
  override suspend fun fromFile(file: VirtualFile): PyDependenciesFile? {
    return file.takeIf { it.name == PIP_FILE_LOCK }?.let { PipFileLockFile(it) }
  }
}
