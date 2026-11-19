// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework

import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pytools.common.FusId
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.createInterpreter

/** The key of an interpreter library entry of [pyProject] that names the native environment of [manager] at [envRef]. */
fun entryKeyOf(pyProject: PyProject, manager: FusId, envRef: String): PythonInterpreterKey =
  PythonInterpreterKey(pyProject.baseDir, PyInterpreterRef(PyInterpreterRef.Mode.Native, manager, PyEnvRef(envRef)))

/**
 * The intention of the creator that the configurator of [toolId] gives for the gone environment of [key], or `null`
 * when it gives none, see [PyProjectSdkConfigurationExtension.prepareSdkCreatorFor]. No tool runs.
 */
suspend fun recreateIntentionOf(toolId: ToolId, pyProject: PyProject, key: PythonInterpreterKey): String? {
  val configurator = PyProjectSdkConfigurationExtension.EP_NAME.extensionList.firstOrNull { it.toolId == toolId }
                     ?: error("The configurator of $toolId must be registered")
  val info = configurator.prepareSdkCreatorFor(pyProject, key) ?: return null
  check(info is CreateInterpreterInfo.WillCreateEnv) { "A gone environment is made, so the creator writes files, got $info" }
  return info.intentionName
}

/**
 * Makes the gone environment of [key] again with the configurator of [toolId], and returns the interpreter that comes
 * out. The tool runs. Fails when the configurator gives no creator, or when the creation fails.
 */
suspend fun recreateEnvironmentFor(toolId: ToolId, pyProject: PyProject, key: PythonInterpreterKey): PythonInterpreter {
  val configurator = PyProjectSdkConfigurationExtension.EP_NAME.extensionList.firstOrNull { it.toolId == toolId }
                     ?: error("The configurator of $toolId must be registered")
  val info = configurator.prepareSdkCreatorFor(pyProject, key) ?: error("The configurator of $toolId gives no creator for $key")
  check(info is CreateInterpreterInfo.WillCreateEnv) { "A gone environment is made, so the creator writes files, got $info" }
  return info.createInterpreter(pyProject.residesOnModule.name).orThrow()
}

