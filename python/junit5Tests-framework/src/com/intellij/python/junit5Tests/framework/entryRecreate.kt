// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework

import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pytools.common.FusId
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension

/** The key of an interpreter library entry of [pyProject] that names the native environment of [manager] at [envRef]. */
fun entryKeyOf(pyProject: PyProject, manager: FusId, envRef: String): PythonInterpreterKey =
  PythonInterpreterKey(pyProject.baseDir, PyInterpreterRef(PyInterpreterRef.Mode.Native, manager, PyEnvRef(envRef)))

/**
 * The intention of the creator that the configurator of [toolId] gives for the gone environment of [key], or `null`
 * when it gives none, see [PyProjectSdkConfigurationExtension.prepareSdkCreatorFor]. The creator is asked for, never
 * called, so no tool runs. Fails when no configurator of [toolId] is registered, or when the creator would not make the
 * environment.
 */
suspend fun recreateIntentionOf(toolId: ToolId, pyProject: PyProject, key: PythonInterpreterKey): String? {
  val configurator = PyProjectSdkConfigurationExtension.EP_NAME.extensionList.firstOrNull { it.toolId == toolId }
                     ?: error("The configurator of $toolId must be registered")
  val info = configurator.prepareSdkCreatorFor(pyProject, key) ?: return null
  check(info is CreateInterpreterInfo.WillCreateEnv) { "A gone environment is made, so the creator writes files, got $info" }
  return info.intentionName
}
