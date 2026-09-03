// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyInterpreterVersionUtil")

package com.jetbrains.python.target

import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.remote.RemoteSdkException
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.ui.pyMayBeModalBlocking
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * What to run for this interpreter, or `null` when it records no path to run.
 *
 * An SDK still being set up has none yet: `interpreterPath` comes from the target, and the caller can reach this while
 * the target is still being asked. It used to be dereferenced regardless, which turned that moment into a
 * `NullPointerException` from inside a version probe.
 */
private fun PyTargetAwareAdditionalData.getBinaryToExec(): BinaryToExec? {
  val interpreterPath = interpreterPath ?: return null
  val configuration = targetEnvironmentConfiguration ?: return BinOnEel(Path.of(interpreterPath))
  return BinOnTarget(interpreterPath, configuration)
}

internal suspend fun PyTargetAwareAdditionalData.getInterpreterVersion(): PyResult<LanguageLevel> {
  val binary = getBinaryToExec() ?: return PyResult.localizedError(message("python.sdk.target.interpreter.path.not.recorded"))
  return binary.validatePythonAndGetInfo().mapSuccess { it.languageLevel }
}


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