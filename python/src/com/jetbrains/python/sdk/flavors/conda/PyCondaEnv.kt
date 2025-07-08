// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.conda.TargetCommandExecutor
import com.jetbrains.python.sdk.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv.Companion.getEnvs
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * TODO: Once we get rid of [TargetCommandExecutor] and have access to [TargetEnvironmentConfiguration] use it validate conda binary in [getEnvs]
 * @see `PyCondaTest`
 */
@ApiStatus.Internal

data class PyCondaEnv(
  val envIdentity: PyCondaEnvIdentity,
  val fullCondaPathOnTarget: FullPathOnTarget,
) {
  val condaPath: Path
    get() = Path(fullCondaPathOnTarget)

  companion object {

    /**
     * @return list of conda environments
     */
    @ApiStatus.Internal
    suspend fun getEnvs(condaPath: String): PyResult<List<PyCondaEnv>> {
      val info = CondaExecutor.listEnvs(Path(condaPath)).getOr { return it }
      val envs = info.envs.distinctBy { it.trim().lowercase(Locale.getDefault()) }
      val identities = envs.map { envPath ->
        // Env name is the basename for envs inside of default location
        // envPath should be direct child of envs_dirs to be a NamedEnv
        val isEnvName = info.envsDirs.any {
          Path.of(it) == Path.of(envPath).parent
        }
        val envName = if (isEnvName)
          Path.of(envPath).name
        else
          null
        val base = envPath.equals(info.condaPrefix, ignoreCase = true)
        val identity = if (envName != null) {
          PyCondaEnvIdentity.NamedEnv(envName)
        }
        else {
          PyCondaEnvIdentity.UnnamedEnv(envPath, base)
        }
        PyCondaEnv(identity, condaPath)
      }

      return PyResult.success(identities)
    }

    suspend fun createEnv(command: PyCondaCommand, newCondaEnvInfo: NewCondaEnvRequest): PyResult<Unit> {
      return newCondaEnvInfo.create(command.getCondaPath())
    }
  }

  suspend fun createSdkFromThisEnv(targetConfig: TargetEnvironmentConfiguration?, existingSdk: List<Sdk>): Sdk =
    PyCondaCommand(fullCondaPathOnTarget, targetConfig).createCondaSdkFromExistingEnv(envIdentity, existingSdk, null)


  /**
   * Add conda prefix to [targetedCommandLineBuilder]
   */
  fun addCondaToTargetBuilder(targetedCommandLineBuilder: TargetedCommandLineBuilder) {
    targetedCommandLineBuilder.apply {
      setExePath(fullCondaPathOnTarget)
      addParameter("run")
      when (val identity = this@PyCondaEnv.envIdentity) {
        is PyCondaEnvIdentity.UnnamedEnv -> {
          addParameter("-p")
          addParameter(identity.envPath) // TODO: Escape. Shouldn't target have something like "addEscaped"?
        }
        is PyCondaEnvIdentity.NamedEnv -> {
          addParameter("-n")
          addParameter(identity.envName)
        }
      }
      // Otherwise we wouldn't have interactive output (for console etc.)
      addParameter("--no-capture-output")
    }
  }

  override fun toString(): String = "$envIdentity@$fullCondaPathOnTarget"
}


/**
 * Request to create new conda environment.
 */
sealed class NewCondaEnvRequest {
  abstract val envName: @NonNls String

  @ApiStatus.Internal
  abstract suspend fun create(condaPath: Path): PyResult<Unit>

  /**
   * Create empty environment with [langLevel]
   */
  class EmptyNamedEnv(private val langLevel: LanguageLevel, @get:NonNls override val envName: String) : NewCondaEnvRequest() {

    @ApiStatus.Internal
    override suspend fun create(condaPath: Path): PyResult<Unit> {
      return CondaExecutor.createNamedEnv(condaPath, envName, langLevel.toPythonVersion())
    }
  }

  /**
   * Create empty environment with [langLevel] in a specific directory
   */
  class EmptyUnnamedEnv(private val langLevel: LanguageLevel, private val envPrefix: String) : NewCondaEnvRequest() {
    override val envName: String get() = envPrefix

    @ApiStatus.Internal
    override suspend fun create(condaPath: Path): PyResult<Unit> {
      return CondaExecutor.createUnnamedEnv(condaPath, envPrefix, langLevel.toPythonVersion())
    }
  }

  /**
   * Create env based om [environmentYaml].
   * Only local target is supported since we do not have an API to read remote file (yet)
   * TODO: Support remote env creation
   * @see [LocalEnvByLocalEnvironmentFile]
   */
  class LocalEnvByLocalEnvironmentFile(private val environmentYaml: Path) : NewCondaEnvRequest() {
    init {
      assert(environmentYaml.exists()) { "$environmentYaml doesn't exist" }
    }

    override val envName: String = readEnvName()

    @ApiStatus.Internal
    override suspend fun create(condaPath: Path): PyResult<Unit> {
      return CondaExecutor.createFileEnv(condaPath, environmentYaml)
    }

    private fun readEnvName(): String = runReadAction {
      val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(environmentYaml) ?: return@runReadAction "default"
      CondaEnvironmentYmlParser.readNameFromFile(virtualFile) ?: "default"
    }
  }
}