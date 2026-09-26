// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.conda.environmentYml

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.requirements.PyDependenciesFile
import com.jetbrains.python.requirements.PyDependenciesFileProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CondaEnvironmentYmlFileProvider : PyDependenciesFileProvider {
  override suspend fun fromFile(file: VirtualFile): PyDependenciesFile? = when (file.name) {
    CondaEnvironmentYmlSdkUtils.ENV_YML_FILE_NAME,
    CondaEnvironmentYmlSdkUtils.ENV_YAML_FILE_NAME,
      -> CondaEnvironmentYmlFile(file)
    else -> null
  }
}
