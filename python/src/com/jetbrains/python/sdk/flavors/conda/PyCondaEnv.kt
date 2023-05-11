// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.gson.Gson
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.createProcessWithResult
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.add.target.conda.TargetCommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists

/**
 * @see [com.jetbrains.env.conda.PyCondaTest]
 */
data class PyCondaEnv(val envIdentity: PyCondaEnvIdentity,
                      val fullCondaPathOnTarget: FullPathOnTarget) {

  companion object {
    /**
     * @return list of conda environments
     */
    @ApiStatus.Internal
    suspend fun getEnvs(command: TargetCommandExecutor,
                        fullCondaPathOnTarget: FullPathOnTarget): Result<List<PyCondaEnv>> = withContext(Dispatchers.IO) {
      val json = command.execute(listOf(fullCondaPathOnTarget, "info", "--envs", "--json")).thenApply { it.stdout }.await()
      return@withContext kotlin.runCatching { // External command may return junk
        val info = Gson().fromJson(json, CondaInfoJson::class.java)
        val fileSeparator = command.targetPlatform.await().platform.fileSeparator
        info.envs.distinctBy { it.trim().lowercase(Locale.getDefault()) }.map { envPath ->
          // Env name is the basename for envs inside of default location
          val envName = if (info.envs_dirs.any { envPath.startsWith(it) }) envPath.split(fileSeparator).last() else null
          val base = envPath.equals(info.conda_prefix, ignoreCase = true)
          PyCondaEnv(envName?.let { PyCondaEnvIdentity.NamedEnv(it) } ?: PyCondaEnvIdentity.UnnamedEnv(envPath, base),
                     fullCondaPathOnTarget)

        }
      }
    }

    suspend fun createEnv(command: PyCondaCommand, newCondaEnvInfo: NewCondaEnvRequest): Result<Process> {

      val (_, env, commandLineBuilder) = command.createRequestEnvAndCommandLine().getOrElse { return Result.failure(it) }

      val commandLine = commandLineBuilder.apply {
        //conda create -y -n myenv python=3.9
        //addParameters("create", "-y", "-n", envName, *newCondaEnvInfo.createEnvArguments)
        addParameters(*newCondaEnvInfo.createEnvArguments)
      }.build()
      val process = env.createProcessWithResult(commandLine).getOrElse { return Result.failure(it) }
      return Result.success(process)
    }

  }


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

    override val createEnvArguments: Array<String> = arrayOf("env", "create", "--force", "-f", environmentYaml.toFile().path)
  }
}