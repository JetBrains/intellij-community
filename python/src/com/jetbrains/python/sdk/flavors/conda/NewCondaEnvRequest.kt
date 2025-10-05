// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import kotlin.io.path.exists

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
  @ApiStatus.Internal
  class LocalEnvByLocalEnvironmentFile(private val environmentYaml: Path, val existingEnvs: List<PyCondaEnv>) : NewCondaEnvRequest() {
    init {
      assert(environmentYaml.exists()) { "$environmentYaml doesn't exist" }
    }

    override val envName: String = readEnvName()

    @ApiStatus.Internal
    override suspend fun create(condaPath: Path): PyResult<Unit> {
      //If environment already exists we need to update it because it cannot be recreated on windows
      val existingNames = existingEnvs.mapNotNull { (it.envIdentity as? PyCondaEnvIdentity.NamedEnv)?.envName }
      return if (envName != DEFAULT_ENV_NAME && envName in existingNames) {
        CondaExecutor.updateFromEnvironmentFile(condaPath,
                                                environmentYaml.toString(),
                                                PyCondaEnvIdentity.NamedEnv(envName))
      }
      else {
        CondaExecutor.createFileEnv(condaPath, environmentYaml)
      }
    }

    private fun readEnvName(): String = runReadAction {
      val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(environmentYaml) ?: return@runReadAction DEFAULT_ENV_NAME
      CondaEnvironmentYmlParser.readNameFromFile(virtualFile) ?: DEFAULT_ENV_NAME
    }
  }

  @ApiStatus.Internal
  companion object {
    private const val DEFAULT_ENV_NAME = "default"
  }
}