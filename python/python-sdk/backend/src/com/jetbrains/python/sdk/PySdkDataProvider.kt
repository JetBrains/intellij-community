// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import java.nio.file.Path
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.python.pytools.common.FusId
import com.intellij.python.sdk.common.PyEnvRef
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import org.jetbrains.annotations.ApiStatus

/**
 * Builds the SDK data of an existing environment of one tool, [manager]. The `PyEvoEnvironmentProvider` of that tool
 * finds the environment, and the `FileSystem` of its machine puts the two together, see `FileSystem.sdkHomeAndData`.
 */
@ApiStatus.Internal
interface PySdkDataProvider {
  /** The tool whose environments this provider builds SDK data for, by the `fusId` of its `PyTool`. */
  val manager: FusId

  /**
   * The SDK data of the existing environment of [manager] at [envRef] in [pyProject], whose Python binary is
   * [pythonBinary] on the machine of [fileSystem]. This is what the tool stores on the SDK of that environment: its
   * flavor data, its working directory and its dependency file. A target file system wraps the data for its target.
   *
   * [requirementsFile] is the dependency file to store, relative to the base directory of [pyProject], see
   * [PythonSdkAdditionalData.setRequirementsPath]. A caller gives it when it knows the file, such as a flow that has
   * just created the environment. `null` keeps the default of the tool.
   */
  suspend fun <P : PathHolder> sdkDataOf(
    pyProject: PyProject,
    envRef: PyEnvRef,
    pythonBinary: P,
    fileSystem: FileSystem<P>,
    requirementsFile: Path? = null,
  ): PythonSdkAdditionalData

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PySdkDataProvider> = ExtensionPointName.create("Pythonid.pySdkDataProvider")
  }
}
