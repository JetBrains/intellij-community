package com.intellij.python.junit5Tests.framework

import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfoRt.*
import com.intellij.util.io.awaitExit
import com.jetbrains.python.sdk.configureBuilderToRunPythonOnTarget
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.MacPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.WinPythonSdkFlavor
import com.jetbrains.python.sdk.sdkFlavor
import com.jetbrains.python.sdk.sdkSeemsValid
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import com.jetbrains.python.statistics.version
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

// CPython (vanilla) flavor
val osSpecificSdkFlavorClass: KClass<out CPythonSdkFlavor<*>> = when {
  isMac -> MacPythonSdkFlavor::class
  isWindows -> WinPythonSdkFlavor::class
  isLinux -> UnixPythonSdkFlavor::class
  else -> error("Current OS not supported")
}

/**
 * Freshly created SDK must have mandatory fields and be usable.
 * if [project] set we also check sdk was persisted correctly
 */
fun ensureSdkIsUsable(sdk: Sdk, expectedSdkFlavorClass: KClass<out CPythonSdkFlavor<*>> = osSpecificSdkFlavorClass, project: Project? = null) {
  assertNotNull(sdk.version, "$sdk must have version")
  // Both fixture and extension filter py2 out
  assertTrue(sdk.version.isPy3K, "$sdk must be Py3 because all JUnit5 tests accept Py3 only")
  assertNotNull(sdk.name, "$sdk must have name")
  assertNotNull(sdk.homePath, "$sdk must have homePath")
  assertEquals(expectedSdkFlavorClass, sdk.sdkFlavor::class)
  assertTrue(sdk.sdkSeemsValid)

  project?.pySdkService?.ensureSdkSaved(sdk)

  // Execute something on SDK

  val targetEnvRequest = LocalTargetEnvironmentRequest()
  val targetCommandLineBuilder = TargetedCommandLineBuilder(targetEnvRequest)

  sdk.configureBuilderToRunPythonOnTarget(targetCommandLineBuilder)
  // builder is now ready to run python on target
  targetCommandLineBuilder.addParameters("-c", "print(1)")

  val targetEnvironment = targetEnvRequest.prepareEnvironment(TargetProgressIndicator.EMPTY)
  val process = targetEnvironment.createProcess(targetCommandLineBuilder.build())
  runBlocking {
    withTimeout(40.seconds) {
      assertEquals(0, process.awaitExit())
    }
  }
}