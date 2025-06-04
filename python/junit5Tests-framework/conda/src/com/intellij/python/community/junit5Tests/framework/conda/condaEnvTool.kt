// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.junit5Tests.framework.conda

import com.intellij.execution.processTools.getResultStdoutStr
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
/**
 * Create conda env in [pathToCreateNewEnvIn] using [existingEnv] as a base
 */
suspend fun createCondaEnv(
  existingEnv: PyCondaEnv,
  pathToCreateNewEnvIn: Path,
): PyCondaEnv {
  val process = PyCondaEnv.createEnv(
    PyCondaCommand(existingEnv.fullCondaPathOnTarget, null),
    NewCondaEnvRequest.EmptyUnnamedEnv(LanguageLevel.PYTHON311, pathToCreateNewEnvIn.pathString)
  ).getOrThrow()
  process.getResultStdoutStr().getOrThrow()

  val env = PyCondaEnv(PyCondaEnvIdentity.UnnamedEnv(pathToCreateNewEnvIn.pathString, false), existingEnv.fullCondaPathOnTarget)
  return env
}