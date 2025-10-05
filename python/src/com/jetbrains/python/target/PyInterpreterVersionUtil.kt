// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyInterpreterVersionUtil")

package com.jetbrains.python.target

import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.python.validatePythonAndGetVersion
import com.intellij.remote.RemoteSdkException
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.ui.pyMayBeModalBlocking
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
suspend fun PyTargetAwareAdditionalData.getInterpreterVersion(): PyResult<LanguageLevel> =
  BinOnTarget(interpreterPath, targetEnvironmentConfiguration).validatePythonAndGetVersion()


@ApiStatus.Internal
@Throws(RemoteSdkException::class)
fun PyTargetAwareAdditionalData.getInterpreterVersionForJava(): LanguageLevel {
  val r = pyMayBeModalBlocking {
    getInterpreterVersion()
  }
  return when (r) {
    is Result.Failure -> throw RemoteSdkException(r.error.message)
    is Result.Success -> r.result
  }
}