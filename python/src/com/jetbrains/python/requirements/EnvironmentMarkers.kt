// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime

const val PLATFORM_RELEASE = "platform_release"
const val PYTHON_VERSION = "python_version"
const val PYTHON_FULL_VERSION = "python_full_version"
const val IMPLEMENTATION_VERSION = "implementation_version"
const val PACKAGE_VERSION = "package_version"

val VERSION_VARIABLES = listOf(IMPLEMENTATION_VERSION, PLATFORM_RELEASE, PYTHON_FULL_VERSION, PYTHON_VERSION, PACKAGE_VERSION)

@Serializable
data class PythonInfo(
  var osName: String? = null,
  var sysPlatform: String? = null,
  var platformMachine: String? = null,
  var platformPythonImplementation: String? = null,
  var platformRelease: String? = null,
  var platformSystem: String? = null,
  var platformVersion: String? = null,
  var pythonVersion: String? = null,
  var pythonFullVersion: String? = null,
  var implementationName: String? = null,
  var implementationVersion: String? = null,
  var extra: String? = null
) {
  val map: Map<String, String?>
    get() {
      return mapOf(
        "os_name" to osName,
        "sys_platform" to sysPlatform,
        "platform_machine" to platformMachine,
        "platform_python_implementation" to platformPythonImplementation,
        "platform_release" to platformRelease,
        "platform_system" to platformSystem,
        "platform_version" to platformVersion,
        "python_version" to pythonVersion,
        "python_full_version" to pythonFullVersion,
        "implementation_name" to implementationName,
        "implementation_version" to implementationVersion,
        "extra" to extra
      )
    }
}

val markersCache = mutableMapOf<String, Pair<PythonInfo, LocalDateTime>>()

fun getPythonInfo(sdk: Sdk): PythonInfo {
  val cached = markersCache[sdk.name]
  if (cached != null) {
    val actual = cached.second.plusDays(1).isAfter(LocalDateTime.now())
    if (actual) {
      return cached.first
    }
  }
  val scriptResource = object {}.javaClass.getResource("/python_info.py")
  val code = scriptResource?.readText()
  val result = code?.let { execPythonCode(sdk, it) } ?: return PythonInfo()
  val pythonInfo = Json.decodeFromString<PythonInfo>(result)
  markersCache[sdk.name] = pythonInfo to LocalDateTime.now()

  return pythonInfo
}

fun execPythonCode(sdk: Sdk, code: String): String? {
  if (sdk.sdkType !is PythonSdkType) {
    return null
  }

  val pythonPath = sdk.homePath ?: return null

  val output = ApplicationManager.getApplication()
                 .executeOnPooledThread<ProcessOutput> {
                   return@executeOnPooledThread PySdkUtil.getProcessOutput(
                     File(pythonPath).parent,
                     listOf(sdk.homePath, "-c", code).toTypedArray(),
                     5000
                   )
                 }.get() ?: return null
  if (output.exitCode != 0 || output.isTimeout || output.isCancelled) {
    return null
  }
  return output.stdout
}
