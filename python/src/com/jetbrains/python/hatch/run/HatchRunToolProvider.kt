// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.run

import com.intellij.python.hatch.HatchExecutableNotFoundHatchError
import com.jetbrains.python.run.features.PyInterpreterRunToolProvider
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.pytools.common.FusId
import com.intellij.python.hatch.HatchPyTool
import com.intellij.python.hatch.impl.sdk.HatchSdkFlavor
import com.intellij.python.hatch.runtime.HatchConstants
import com.intellij.python.pytools.resolveExecutable
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.features.PyRunToolData
import com.jetbrains.python.run.features.PyRunToolId
import com.jetbrains.python.run.features.PyRunToolParameters
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import java.nio.file.Path

internal class HatchRunToolProvider : PyInterpreterRunToolProvider() {
  override val manager: FusId get() = HatchPyTool.getInstance().fusId

  override suspend fun <P : PathHolder> getRunToolParameters(
    interpreter: PythonInterpreter,
    fileSystem: FileSystem<P>,
    inlineScriptTarget: Path?,
  ): PyRunToolParameters {
    val hatchPath = HatchPyTool.getInstance().resolveExecutable(fileSystem)
                    ?: throw AssertionError(HatchExecutableNotFoundHatchError(null))
    // The ref names the Hatch environment, see HatchSdkFlavor.toolEnvRefOf.
    val env = mapOf(HatchConstants.AppEnvVars.ENV to interpreter.ref.envRef.value)
    return PyRunToolParameters(hatchPath, listOf("run", "python"), env, includeOriginalExe = false)
  }

  override val runToolData: PyRunToolData = PyRunToolData(
    PyRunToolId("hatch.run"),
    PyBundle.message("hatch.run.configuration.type.display.name"),
    PyBundle.message("python.run.configuration.fragments.python.group"),
    label = PyBundle.message("hatch.run.tool.label"),
  )

  override val initialToolState: Boolean = true
}
