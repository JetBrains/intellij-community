// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.conda

import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.sdk.backend.evolution.toolMissing
import com.intellij.python.sdk.backend.resolveExecutable
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import org.jetbrains.annotations.ApiStatus

/** Whether [envRef] names a conda env by its path, not by its name. A conda env name has no path separator. */
internal fun isCondaPathEnvRef(envRef: PyEnvRef): Boolean = envRef.value.any { it == '/' || it == '\\' }

/**
 * The identity of the conda env at [envRef]: an env created with `-p` for a path, else a named env. A path to the root
 * of a conda installation also works, because `conda run -p` accepts the base env.
 */
internal fun condaIdentityOf(envRef: PyEnvRef): PyCondaEnvIdentity =
  if (isCondaPathEnvRef(envRef)) PyCondaEnvIdentity.UnnamedEnv(envRef.value, isBase = false)
  else PyCondaEnvIdentity.NamedEnv(envRef.value)

/**
 * The Python binary of the conda env at [envRef] on the machine of [fileSystem], as conda reports it. conda finds the
 * env itself, so an env that was created a moment ago is found too. When there is no such env, the conda error is the
 * result.
 */
@ApiStatus.Internal
suspend fun <P : PathHolder> condaPythonBinaryOf(fileSystem: FileSystem<P>, envRef: PyEnvRef): PyResult<P> {
  val conda = CondaPyTool.getInstance()
  val condaExecutable = fileSystem.resolveExecutable(conda) ?: return toolMissing(conda)
  val interpreterPath = CondaExecutor.runPythonInCondaEnv(
    fileSystem.getBinaryToExec(condaExecutable), condaIdentityOf(envRef), "-c", "import sys; print(sys.executable)",
  ).getOr { return it }
  return fileSystem.parsePath(interpreterPath.trim())
}
