// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Creates and persists sdk.
 * [projectPath] is used to suggest name
 */
@ApiStatus.Internal
suspend fun createSdk(
  pythonBinaryPath: VirtualFile,
  projectPath: Path?,
  existingSdks: Array<Sdk>,
): Sdk {
  val newSdk = withContext(Dispatchers.IO) {
    // "suggest name" calls external process and can't be called from EDT
    val suggestedName = /*suggestedSdkName ?:*/ suggestAssociatedSdkName(pythonBinaryPath.path, projectPath?.toString())
    SdkConfigurationUtil.setupSdk(existingSdks, pythonBinaryPath,
                                  PythonSdkType.getInstance(),
                                  null, suggestedName)
  }

  newSdk.persist()
  return newSdk
}


