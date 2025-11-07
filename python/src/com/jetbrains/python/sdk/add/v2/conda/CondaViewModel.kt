// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.conda

import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.intellij.platform.eel.provider.localEel
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.conda.loadLocalPythonCondaPath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.packaging.conda.environmentYml.CondaEnvironmentYmlSdkUtils
import com.jetbrains.python.packaging.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.conda.TargetEnvironmentRequestCommandExecutor
import com.jetbrains.python.sdk.conda.suggestCondaPath
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

private val LOG: Logger = fileLogger()

class CondaViewModel<P : PathHolder>(
  val fileSystem: FileSystem<P>,
  propertyGraph: PropertyGraph,
  val projectPathFlows: ProjectPathFlows,
) : PythonToolViewModel {
  val condaExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)
  val condaEnvironmentsResult: MutableStateFlow<PyResult<List<PyCondaEnv>>?> = MutableStateFlow(null)
  val condaEnvironmentsLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val selectedCondaEnv: ObservableMutableProperty<PyCondaEnv?> = propertyGraph.property(null)
  val baseCondaEnv: ObservableMutableProperty<PyCondaEnv?> = propertyGraph.property(null)
  val newCondaEnvName: ObservableMutableProperty<String> = propertyGraph.property("")
  lateinit var scope: CoroutineScope

  val toolValidator: ToolValidator<P> = ToolValidator(
    fileSystem = fileSystem,
    toolVersionPrefix = "conda",
    backProperty = condaExecutable,
    propertyGraph = propertyGraph,
    defaultPathSupplier = {
      val eelApi = (fileSystem as? FileSystem.Eel)?.eelApi
      if (eelApi == localEel) {
        loadLocalPythonCondaPath()?.let {
          return@ToolValidator PathHolder.Eel(it) as P?
        }
      }

      fileSystem.which("conda")?.let { return@ToolValidator it }

      // legacy slow fallback detection via the defined list of paths in case of there is no conda on the PATH (PY-85060),
      // not sure if it is worth it to keep it, because if there is no conda on the PATH the installation might be broken
      val targetEnvironmentConfiguration = (fileSystem as? FileSystem.Target)?.targetEnvironmentConfiguration
      val request = targetEnvironmentConfiguration?.createEnvironmentRequest(project = null) ?: LocalTargetEnvironmentRequest()
      val executor = TargetEnvironmentRequestCommandExecutor(request)
      val suggestedCondaPath = runCatching {
        suggestCondaPath(targetCommandExecutor = executor)
      }.getOrElse {
        rethrowControlFlowException(it)
        LOG.warn(it)
        null
      }
      suggestedCondaPath?.let {
        fileSystem.parsePath(suggestedCondaPath).successOrNull
      }
    }
  )

  override fun initialize(scope: CoroutineScope) {
    toolValidator.initialize(scope)
    this.scope = scope

    condaExecutable.afterChange { condaExecutable ->
      condaEnvironmentsResult.value = null
      selectedCondaEnv.set(null)
      baseCondaEnv.set(null)

      if (condaExecutable?.validationResult?.successOrNull != null) {
        detectCondaEnvironments()
      }
    }
  }

  fun detectCondaEnvironments() {
    condaEnvironmentsLoading.value = true
    scope.launch(Dispatchers.UI) {
      condaEnvironmentsResult.value = updateCondaEnvironments()
    }.invokeOnCompletion {
      condaEnvironmentsLoading.value = false
    }
  }

  suspend fun updateSelection(environments: List<PyCondaEnv>): Unit =
    selectedCondaEnv.set(getBestCondaEnv(environments) ?: environments.firstOrNull())

  private suspend fun getBestCondaEnv(environments: List<PyCondaEnv>): PyCondaEnv? = withContext(Dispatchers.IO) {
    val projectPath = projectPathFlows.projectPathWithDefault.first()
    val environmentYml = if (fileSystem.isLocal) getEnvironmentYml(projectPath) else null
    val envName = environmentYml?.let { CondaEnvironmentYmlParser.readNameFromFile(it) }
    val envPrefix = environmentYml?.let { CondaEnvironmentYmlParser.readPrefixFromFile(it) }
    val shouldGuessEnvPrefix = envName == null && envPrefix == null
    environments.firstOrNull {
      val envIdentity = it.envIdentity
      when (envIdentity) {
        is PyCondaEnvIdentity.NamedEnv -> envIdentity.envName == envName
        is PyCondaEnvIdentity.UnnamedEnv -> if (shouldGuessEnvPrefix) {
          val envPath = envIdentity.envPath.toNioPathOrNull()
          !envIdentity.isBase && projectPath == envPath?.parent
        }
        else envIdentity.envPath == envPrefix
      }
    }
  }

  private fun getEnvironmentYml(projectPath: Path) = listOf(
    CondaEnvironmentYmlSdkUtils.ENV_YAML_FILE_NAME,
    CondaEnvironmentYmlSdkUtils.ENV_YML_FILE_NAME,
  ).firstNotNullOfOrNull {
    val path = projectPath.resolve(it)
    path.refreshAndFindVirtualFileOrDirectory()?.takeIf { virtualFile -> virtualFile.isFile }
  }

  private suspend fun updateCondaEnvironments(): PyResult<List<PyCondaEnv>> = withContext(Dispatchers.IO) {
    val executable = condaExecutable.get()
    if (executable == null) return@withContext PyResult.localizedError(message("python.sdk.conda.no.exec"))
    executable.validationResult.getOr { return@withContext it }

    val binaryToExec = executable.pathHolder?.let { fileSystem.getBinaryToExec(it) }!!
    val environments = PyCondaEnv.getEnvs(binaryToExec).getOr { return@withContext it }
    val baseConda = environments.find { env -> env.envIdentity.let { it is PyCondaEnvIdentity.UnnamedEnv && it.isBase } }

    withContext(Dispatchers.UI) {
      baseCondaEnv.set(baseConda)
    }

    return@withContext PyResult.success(environments)
  }
}
