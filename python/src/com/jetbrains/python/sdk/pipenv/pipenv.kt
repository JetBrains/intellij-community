// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.progress.withProgressText
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.target.ui.TargetPanelExtension
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

/**
 * Tells if the SDK was added as a pipenv.
 */
internal val Sdk.isPipEnv: Boolean
  get() = PythonSdkUtil.isPythonSdk(this) && pySdkAdditionalData.flavor == PyPipEnvSdkFlavor

@Internal
fun suggestedSdkName(basePath: @NlsSafe String): @NlsSafe String = "Pipenv (${PathUtil.getFileName(basePath)})"

/**
 * Adopts the existing pipenv environment at [pythonBinaryPath] as a pipenv-typed SDK for [basePath].
 *
 * The counterpart of [setupPipEnvSdkWithProgressReport], which first has to create the environment. Pipenv keeps one
 * environment per project, so [basePath] is what ties the SDK back to it.
 */
internal suspend fun <P : PathHolder> createPipenvSdk(
  basePath: Path,
  pythonBinaryPath: P,
  fileSystem: FileSystem<P>,
  targetPanelExtension: TargetPanelExtension? = null,
): PyResult<Sdk> = withProgressText(PyBundle.message("python.sdk.progress.pipenv.configuring")) {
  fileSystem.setupSdk(
    project = null,
    pythonBinaryPath = pythonBinaryPath,
    sdkAdditionalData = PyPipEnvSdkAdditionalData(basePath),
    targetPanelExtension = targetPanelExtension,
    suggestedSdkName = null,
  )
}
