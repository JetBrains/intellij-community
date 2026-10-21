// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.conda

import java.nio.file.Path
import com.intellij.python.sdk.backend.resolveExecutable
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.PySdkDataProvider
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.pytools.common.FusId
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.PathHolder

/** The SDK data of a conda env, named or `-p`-created. */
internal class CondaSdkDataProvider : PySdkDataProvider {
  override val manager: FusId get() = CondaPyTool.getInstance().fusId

  override suspend fun <P : PathHolder> sdkDataOf(
    pyProject: PyProject,
    envRef: PyEnvRef,
    pythonBinary: P,
    fileSystem: FileSystem<P>,
    requirementsFile: Path?,
  ): PythonSdkAdditionalData {
    // The env ref is the name of a named env, or the path of an unnamed one.
    val home = fileSystem.resolvePythonHome(pythonBinary)
    val envPath = home.toStringOnRemoteMachine()
    val identity = if (isCondaPathEnvRef(envRef)) PyCondaEnvIdentity.UnnamedEnv(envPath, isBase = isCondaRoot(fileSystem, home))
    else PyCondaEnvIdentity.NamedEnv(envRef.value, envPath)
    // conda is found before this runs: the env was listed with it, and its binary was found with it.
    val condaPath = fileSystem.resolveExecutable(CondaPyTool.getInstance())?.toStringOnRemoteMachine().orEmpty()
    return condaSdkDataOf(PyCondaEnv(identity, condaPath), pyProject.baseDir).also { it.requirementsPath = requirementsFile }
  }

  /**
   * Whether [home] is the root of a conda installation, which is its base env. Only the root has `condabin` and `envs`,
   * the same rule as `CondaEnvironment.isBase`. The base env is always unnamed in conda's list, so only an unnamed env
   * can be it.
   */
  private suspend fun <P : PathHolder> isCondaRoot(fileSystem: FileSystem<P>, home: P): Boolean =
    fileSystem.directoryExists(fileSystem.resolveChild(home, "condabin")) || fileSystem.directoryExists(fileSystem.resolveChild(home, "envs"))
}
