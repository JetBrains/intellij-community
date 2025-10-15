// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.conda

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrLogException
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.conda.*
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.persist
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG: Logger = fileLogger()

@RequiresEdt
internal fun PythonAddInterpreterModel<*>.createCondaCommand(): PyResult<PyCondaCommand> {
  val targetEnvironmentConfiguration = (fileSystem as? FileSystem.Target)?.targetEnvironmentConfiguration
  val executable = state.condaExecutable.get() ?: return PyResult.localizedError(message("python.sdk.select.conda.path.title"))
  return PyCondaCommand(
    fullCondaPathOnTarget = executable.pathHolder.toString().convertToPathOnTarget(targetEnvironmentConfiguration),
    targetConfig = targetEnvironmentConfiguration
  ).let { PyResult.success(it) }
}

internal suspend fun PythonAddInterpreterModel<*>.createCondaEnvironment(moduleOrProject: ModuleOrProject, request: NewCondaEnvRequest): PyResult<Sdk> {

  val result = createCondaCommand().getOr { return it }.createCondaSdkAlongWithNewEnv(
    newCondaEnvInfo = request,
    uiContext = Dispatchers.EDT,
    existingSdks = existingSdks,
    project = moduleOrProject.project
  )
    .onSuccess { sdk ->
      (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)

      val module = PyProjectCreateHelpers.getModule(moduleOrProject, null)
      if (module != null) {
        sdk.setAssociationToModule(module)
      }
      sdk.persist()
    }

  return result
}

internal fun TargetEnvironmentConfiguration?.toExecutor(): TargetCommandExecutor {
  return TargetEnvironmentRequestCommandExecutor(this?.createEnvironmentRequest(project = null) ?: LocalTargetEnvironmentRequest())
}

/**
 * [base] or selected
 */
suspend fun PythonAddInterpreterModel<*>.selectCondaEnvironment(base: Boolean): PyResult<Sdk> {
  val pyCondaEnv = if (base) {
    getBaseCondaOrError()
  }
  else {
    state.selectedCondaEnv.get()?.let { PyResult.success(it) } ?: PyResult.localizedError(message("python.sdk.conda.no.env.selected.error"))
  }
    .getOr { return it }
  val existingSdk = ProjectJdkTable.getInstance().findJdk(pyCondaEnv.envIdentity.userReadableName)
  if (existingSdk != null && existingSdk.isCondaVirtualEnv) return PyResult.success(existingSdk)
  val executable = state.condaExecutable.get() ?: return PyResult.localizedError(message("python.sdk.select.conda.path.title"))
  executable.validationResult.getOr { return it }

  val sdk = PyCondaCommand(
    fullCondaPathOnTarget = executable.pathHolder.toString(),
    targetConfig = (fileSystem as? FileSystem.Target)?.targetEnvironmentConfiguration
  ).createCondaSdkFromExistingEnv(
    condaIdentity = pyCondaEnv.envIdentity,
    existingSdks = this@selectCondaEnvironment.existingSdks,
    project = ProjectManager.getInstance().defaultProject,
  )

  (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)
  sdk.persist()
  return PyResult.success(sdk)
}

suspend fun BinaryToExec.getCondaVersion(): PyResult<Version> {
  val version = getToolVersion("conda").getOr { return it }
  return try {
    PyResult.success(version)
  }
  catch (ex: VersionFormatException) {
    PyResult.localizedError(ex.localizedMessage)
  }
}

/**
 * Returns error or `null` if no error
 */
internal suspend fun <P: PathHolder> PythonAddInterpreterModel<P>.detectCondaEnvironments(): PyResult<Unit> = withContext(Dispatchers.IO) {
  val executable = state.condaExecutable.get()
  if (executable == null) return@withContext PyResult.localizedError(message("python.sdk.conda.no.exec"))
  executable.validationResult.getOr { return@withContext it }

  val binaryToExec = executable.pathHolder?.let { fileSystem.getBinaryToExec(it) }!!
  val environments = PyCondaEnv.getEnvs(binaryToExec).getOr { return@withContext it }
  val baseConda = environments.find { env -> env.envIdentity.let { it is PyCondaEnvIdentity.UnnamedEnv && it.isBase } }

  withContext(Dispatchers.EDT) {
    condaEnvironments.value = environments
    state.baseCondaEnv.set(baseConda)
  }
  return@withContext PyResult.success(Unit)
}

internal suspend fun <P: PathHolder> PythonAddInterpreterModel<P>.detectCondaExecutable(): Unit = withContext(Dispatchers.IO) {
  val targetEnvironmentConfiguration = (fileSystem as? FileSystem.Target)?.targetEnvironmentConfiguration
  val executor = targetEnvironmentConfiguration.toExecutor()
  val suggestedCondaPath = runCatching {
    suggestCondaPath(targetCommandExecutor = executor)
  }.getOrLogException(LOG)
  val condaPathOnFS = suggestedCondaPath?.let { fileSystem.parsePath(suggestedCondaPath).getOrLogException(LOG) }

  val executable = if (condaPathOnFS != null) {
    val binaryToExec = fileSystem.getBinaryToExec(condaPathOnFS)
    val versionResult = binaryToExec.getCondaVersion()
    ValidatedPath.Executable(condaPathOnFS, versionResult)
  }
  else {
    ValidatedPath.Executable<P>(null, PyResult.localizedError(message("python.add.sdk.conda.executable.path.is.not.found")))
  }

  withContext(Dispatchers.EDT) {
    state.condaExecutable.set(executable)
  }
}