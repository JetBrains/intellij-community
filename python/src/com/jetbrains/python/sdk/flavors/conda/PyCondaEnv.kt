// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.gson.Gson
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.createProcessWithResult
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.conda.TargetCommandExecutor
import com.jetbrains.python.sdk.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv.Companion.getEnvs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists

/**
 * TODO: Once we get rid of [TargetCommandExecutor] and have access to [com.intellij.execution.target.TargetEnvironmentConfiguration] use it validate conda binary in [getEnvs]
 * @see `PyCondaTest`
 */
data class PyCondaEnv(
  val envIdentity: PyCondaEnvIdentity,
  val fullCondaPathOnTarget: FullPathOnTarget,
) {


  companion object {


    /**
     * @return unparsed output of conda info --envs --json
     */
    private suspend fun getEnvsInfo(command: TargetCommandExecutor, fullCondaPathOnTarget: FullPathOnTarget): Result<String> {
      val output = command.execute(listOf(fullCondaPathOnTarget, "info", "--envs", "--json")).await()
      return if (output.exitCode == 0) Result.success(output.stdout) else com.jetbrains.python.failure(output.stderr)
    }

    /**
     * @return list of conda's envs_dirs directories
     */
    @ApiStatus.Internal
    suspend fun getEnvsDirs(
      command: TargetCommandExecutor,
      fullCondaPathOnTarget: FullPathOnTarget,
    ): Result<Collection<String>> = withContext(Dispatchers.IO) {
      val json = getEnvsInfo(command, fullCondaPathOnTarget).getOrElse { return@withContext Result.failure(it) }
      return@withContext runCatching { // External command may return junk
        val info = Gson().fromJson(json, CondaInfoJson::class.java)
        info.envs_dirs
      }
    }

    /**
     * @return list of conda environments
     */
    @ApiStatus.Internal
    suspend fun getEnvs(
      command: TargetCommandExecutor,
      fullCondaPathOnTarget: FullPathOnTarget,
    ): Result<List<PyCondaEnv>> = withContext(Dispatchers.IO) {
      val json = getEnvsInfo(command, fullCondaPathOnTarget).getOrElse { return@withContext Result.failure(it) }
      return@withContext kotlin.runCatching { // External command may return junk
        val info = Gson().fromJson(json, CondaInfoJson::class.java)
        val fileSeparator = command.targetPlatform.await().platform.fileSeparator
        info.envs.distinctBy { it.trim().lowercase(Locale.getDefault()) }.map { envPath ->
          // Env name is the basename for envs inside of default location
          // envPath should be direct child of envs_dirs to be a NamedEnv
          val envName = if (info.envs_dirs.any {
              if (command.local) Path.of(it) == Path.of(envPath).parent
              else envPath.startsWith(it)
            }) envPath.split(fileSeparator).last()
          else null
          val base = envPath.equals(info.conda_prefix, ignoreCase = true)
          PyCondaEnv(envName?.let { PyCondaEnvIdentity.NamedEnv(it) } ?: PyCondaEnvIdentity.UnnamedEnv(envPath, base),
                     fullCondaPathOnTarget)

        }
      }
    }

    suspend fun createEnv(command: PyCondaCommand, newCondaEnvInfo: NewCondaEnvRequest): Result<Process> {
      val (_, env, commandLineBuilder) = withContext(Dispatchers.IO) {
        command.createRequestEnvAndCommandLine()
      }.getOrElse { return Result.failure(it) }


      val commandLine = commandLineBuilder.apply {
        //conda create -y -n myenv python=3.9
        //addParameters("create", "-y", "-n", envName, *newCondaEnvInfo.createEnvArguments)
        addParameters(*newCondaEnvInfo.createEnvArguments)
      }.build()
      val process = env.createProcessWithResult(commandLine).getOrElse { return Result.failure(it) }
      return Result.success(process)
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

  /**
   * Add conda prefix to [targetedCommandLineBuilder] without specifying the 'run' command
   */
  fun addCondaEnvironmentToTargetBuilder(targetedCommandLineBuilder: TargetedCommandLineBuilder) {
    targetedCommandLineBuilder.apply {
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
      targetedCommandLineBuilder.fixCondaPathEnvIfNeeded(fullCondaPathOnTarget)
    }
  }

  override fun toString(): String = "$envIdentity@$fullCondaPathOnTarget"
}


private class CondaInfoJson {

  lateinit var envs: Collection<String>
    private set

  @Suppress("PropertyName") // JSON conda format
  lateinit var envs_dirs: Collection<String>
    private set

  @Suppress("PropertyName") // JSON conda format
  lateinit var conda_prefix: String
    private set
}

/**
 * Request to create new conda environment.
 * Conda binary must be run with [createEnvArguments]
 */
sealed class NewCondaEnvRequest {
  abstract val envName: @NonNls String

  abstract val createEnvArguments: Array<String>

  /**
   * Create empty environment with [langLevel]
   */
  class EmptyNamedEnv(langLevel: LanguageLevel, @NonNls override val envName: String) : NewCondaEnvRequest() {
    override val createEnvArguments: Array<String> = arrayOf("create", "-y", "-n", envName, "python=${langLevel.toPythonVersion()}")
  }

  /**
   * Create empty environment with [langlevel] in a specific directory
   */
  class EmptyUnnamedEnv(langLevel: LanguageLevel, private val envPrefix: String) : NewCondaEnvRequest() {
    override val envName: String get() = envPrefix

    override val createEnvArguments: Array<String> = arrayOf("create", "-y", "-p", envPrefix, "python=${langLevel.toPythonVersion()}")
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

    private val lazyName = lazy {
      ObjectMapper(YAMLFactory()).readValue(environmentYaml.toFile(), ObjectNode::class.java).get("name").asText()
    }
    override val envName: String get() = lazyName.value

    override val createEnvArguments: Array<String> = arrayOf("env", "create", "-y", "-f", environmentYaml.toFile().path)
  }
}