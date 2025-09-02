// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda.environmentYml

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.associatedModuleDir
import org.jetbrains.annotations.ApiStatus

/**
 * Migrate from the module persistent path to sdk path
 */
@ApiStatus.Internal
object CondaEnvironmentYmlSdkUtils {
  const val ENV_YML_FILE_NAME: String = "environment.yml"
  const val ENV_YAML_FILE_NAME: String = "environment.yaml"

  @JvmStatic
  fun findFile(sdk: Sdk): VirtualFile? {
    val associatedModuleFile = sdk.associatedModuleDir ?: return null
    return associatedModuleFile.findFileByRelativePath(ENV_YML_FILE_NAME)
           ?: associatedModuleFile.findFileByRelativePath(ENV_YAML_FILE_NAME)
  }
}