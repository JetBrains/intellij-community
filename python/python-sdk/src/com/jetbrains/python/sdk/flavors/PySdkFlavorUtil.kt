package com.jetbrains.python.sdk.flavors

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.pathValidation.PlatformAndRoot.Companion.getPlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Checks if file is executable. If no -- returns error
 */
@RequiresBackgroundThread
internal fun getFileExecutionError(@NonNls fullPath: String, targetEnvConfig: TargetEnvironmentConfiguration?): @Nls String? =
  validateExecutableFile(ValidationRequest(fullPath, platformAndRoot = targetEnvConfig.getPlatformAndRoot()))?.message

@RequiresEdt
internal fun getFileExecutionErrorOnEdt(@NonNls fullPath: String, targetEnvConfig: TargetEnvironmentConfiguration?): @Nls String? =
  runWithModalProgressBlocking(ModalTaskOwner.guess(), "...") {
    withContext(Dispatchers.IO) {
      getFileExecutionError(fullPath, targetEnvConfig)
    }
  }