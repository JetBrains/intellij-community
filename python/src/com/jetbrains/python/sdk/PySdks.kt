// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.add.target.createDetectedSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import java.util.stream.Collectors

private val DETECTED_PYTHON_INTERPRETERS_KEY = Key.create<Set<PyDetectedSdk>>("DETECTED_PYTHON_INTERPRETERS")

suspend fun detectSystemWideSdksSuspended(
  module: Module?,
  target: TargetEnvironmentConfiguration? = null,
  context: UserDataHolder,
): List<Sdk> {
  return detectSystemWideSdksSuspended(module, existingSdks = emptyList(), target, context)
}

/**
 * Gather detected SDKs using [detectSystemWideSdksSuspended].
 */

private suspend fun detectSystemWideSdksSuspended(
  module: Module?,
  existingSdks: List<Sdk>,
  target: TargetEnvironmentConfiguration? = null,
  context: UserDataHolder,
): List<Sdk> {
  if (module != null && module.isDisposed) {
    return emptyList()
  }

  val effectiveTarget = target ?: module?.let { PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(it) }?.asTargetConfig
  val baseDirFromContext = context.getUserData(BASE_DIR)
  return service<PySdks>().getOrDetectSdks(effectiveTarget, baseDirFromContext)
    .filter { detectedSdk ->
      existingSdks.none { existingSdk ->
        isSdkSameAs(existingSdk, detectedSdk)
      }
    }.sortedWith(compareBy<PyDetectedSdk>({ it.guessedLanguageLevel },
                                          { it.homePath }).reversed())
}

private fun isSdkSameAs(lhs: Sdk, rhs: Sdk): Boolean {
  return lhs.targetEnvConfiguration == rhs.targetEnvConfiguration &&
         lhs.homePath == rhs.homePath
}

@Service
class PySdks {
  private val storage = TargetStorage()

  suspend fun getOrDetectSdks(
    targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null,
    projectDir: Path? = null,
  ): Set<PyDetectedSdk> {
    val sdks = storage.getUserData(targetEnvironmentConfiguration, DETECTED_PYTHON_INTERPRETERS_KEY)
    return sdks ?: detectSdks(targetEnvironmentConfiguration, projectDir).also {
      storage.putUserData(targetEnvironmentConfiguration, DETECTED_PYTHON_INTERPRETERS_KEY, it)
    }
  }
}

private suspend fun detectSdks(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?, projectDir: Path?): Set<PyDetectedSdk> =
  withContext(Dispatchers.IO) {
    if (targetEnvironmentConfiguration == null) {
      findBaseSdksLocally(projectDir).toSet()
    }
    else {
      tryFindBaseSdksOnTarget(targetEnvironmentConfiguration)
    }
  }

private fun findBaseSdksLocally(projectDir: Path?): List<PyDetectedSdk> {
  val context = UserDataHolderBase()
  projectDir?.let { context.putUserData(BASE_DIR, it) }
  return detectSystemWideSdks(module = null, existingSdks = emptyList(), context).filterNot { PythonSdkUtil.isBaseConda(it.homePath) }
}

private fun tryFindBaseSdksOnTarget(targetEnvironmentConfiguration: TargetEnvironmentConfiguration): Set<PyDetectedSdk> {
  val targetWithMappedLocalVfs = PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(targetEnvironmentConfiguration)
  return if (targetWithMappedLocalVfs != null) {
    val searchRoots = listOf("/usr/bin/", "/usr/local/bin/")
    searchRoots.flatMapTo(mutableSetOf()) { searchRoot ->
      targetWithMappedLocalVfs.getLocalPath(searchRoot)?.tryFindPythonBinaries()?.mapNotNull {
        val pythonBinaryPath = targetWithMappedLocalVfs.getTargetPath(it) ?: return@mapNotNull null
        createDetectedSdk(pythonBinaryPath, targetEnvironmentConfiguration)
      }?.toSet() ?: emptySet()
    }
  }
  else {
    // TODO Try to execute `which python` or introspect the target
    //val request = targetEnvironmentConfiguration.createEnvironmentRequest(project = null)
    //request.prepareEnvironment(TargetProgressIndicator.EMPTY).createProcess()
    emptySet()
  }
}

private val PYTHON_INTERPRETER_NAME_UNIX_PATTERN = Pattern.compile("python\\d(\\.\\d+)")

internal fun Path.tryFindPythonBinaries(): List<Path> =
  runCatching { Files.list(this).filter(Path::looksLikePythonBinary).collect(Collectors.toList()) }.getOrElse { emptyList() }

private fun Path.looksLikePythonBinary(): Boolean =
  // `Files.isExecutable(this)` does not work instead of `Files.isRegularFile(this)` for WSL case
  PYTHON_INTERPRETER_NAME_UNIX_PATTERN.matcher(fileName.toString()).matches() && Files.isRegularFile(this)