// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.cli

import com.intellij.python.hatch.HatchRuntime
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError.ExecException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path

@Suppress("unused")
@Serializable
class Environment(
  /**
   * An environment's type determines which environment plugin will be used for management.
   * The only built-in environment type is virtual, which uses virtual Python environments.
   */
  val type: String,

  /**
   * The python option specifies which version of Python to use, or an absolute path to a Python interpreter:
   */
  val python: String? = null,

  /**
   * The description option is purely informational and is displayed in the output of the env show command:
   */
  val description: String? = null,

  /**
   * A common use case is standalone environments that do not require inheritance nor the installation of the project,
   * such as for linting or sometimes building documentation.
   * Enabling the detached option will make the environment self-referential and will skip project installation:
   */
  val detached: Boolean? = null,

  /**
   * All environments inherit from the environment defined by its template option, which defaults to default.
   */
  val template: String? = null,

  val installer: String? = null,

  /**
   * By default, environments will install your project during creation. To ignore this step, set skip-install to true
   */
  @SerialName("skip-install")
  val skipInstall: Boolean? = null,

  /**
   * If you define environments with dependencies that only slightly differ from their inherited environments,
   * you can use the extra-dependencies option to avoid redeclaring the dependencies option:
   */
  val dependencies: List<String>? = null,
  @SerialName("extra-dependencies")
  val extraDependencies: List<String>? = null,

  /**
   * If your project defines optional dependencies, you can select which groups to install using the features option:
   */
  val features: List<String>? = null,

  /**
   * By default, environments will always reflect the current state of your project on disk,
   * for example, by installing it in editable mode in a Python environment.
   * Set dev-mode to false to disable this behavior and have your project installed only upon creation of a new environment.
   * From then on, you need to manage your project installation manually.
   */
  @SerialName("dev-mode")
  val devMode: Boolean? = null,

  /**
   * You can define named scripts that may be executed or referenced at the beginning of other scripts. Context formatting is supported.
   */
  val scripts: Map<String, List<String>>? = null,

  /**
   * The platforms option indicates the operating systems with which the environment is compatible:
   */
  val platforms: List<String>? = null,

  /**
   * You can run commands immediately after environments install your project.
   */
  @SerialName("post-install-commands")
  val postInstallCommands: List<String>? = null,

  /**
   * You can run commands immediately before environments install your project.
   */
  @SerialName("pre-install-commands")
  val preInstallCommands: List<String>? = null,

  @SerialName("env-include")
  val envInclude: List<String>? = null,

  @SerialName("env-exclude")
  val envExclude: List<String>? = null,
)

typealias Environments = Map<String, Environment>

private const val DEFAULT_ENV_NAME: String = "default"

/**
 * Manage project environments
 */
class HatchEnv(runtime: HatchRuntime) : HatchCommand("env", runtime) {
  enum class CreateResult {
    Created,
    AlreadyExists,
    NotDefinedInConfig,
  }

  /**
   * Create environments.
   *
   * @return true if created, false if already exists
   */
  suspend fun create(envName: String? = null): Result<CreateResult, ExecException> {
    val arguments = if (envName == null) emptyArray() else arrayOf(envName)
    return executeAndHandleErrors("create", *arguments) {
      val actualEnvName = envName ?: DEFAULT_ENV_NAME
      when {
        it.exitCode == 0 && it.stderr.startsWith("Creating environment") -> Result.success(CreateResult.Created)
        it.exitCode == 0 -> Result.success(CreateResult.AlreadyExists)
        it.stderr.startsWith("Environment `$actualEnvName` is not defined by project config") -> Result.success(CreateResult.NotDefinedInConfig)
        else -> Result.failure(null)
      }
    }
  }

  /**
   * Locate environments.
   *
   * @return path to environment
   */
  suspend fun find(envName: String? = null): Result<Path?, ExecException> {
    val arguments = if (envName == null) emptyArray() else arrayOf(envName)
    return executeAndHandleErrors("find", *arguments) {
      when (it.exitCode) {
        0 -> Result.success(Path.of(it.stdout.trim()))
        else -> {
          if (it.stderr.startsWith("Environment `${envName ?: DEFAULT_ENV_NAME}` is not defined by project config")) {
            Result.success(null)
          }
          else {
            Result.failure(null)
          }
        }
      }
    }
  }

  enum class RemoveResult {
    Removed,
    NotExists,
    NotDefinedInConfig,
    CantRemoveActiveEnvironment
  }

  /**
   * Removes a specified environment or the default environment if none is provided.
   *
   * @param envName The name of the environment to be removed. If null, the default environment will be targeted.
   * @return A Result instance containing:
   * - [RemoveResult.Removed] if the environment was successfully removed.
   * - [RemoveResult.NotExists] if the environment does not exist.
   * - [RemoveResult.NotDefinedInConfig] if the environment is not defined in the project configuration.
   * - [RemoveResult.CantRemoveActiveEnvironment] if the environment cannot be removed because it is currently active.
   * - An error wrapped in [ExecException] in case of execution failure.
   */
  suspend fun remove(envName: String? = null): Result<RemoveResult, ExecException> {
    val arguments = if (envName == null) emptyArray() else arrayOf(envName)
    return executeAndHandleErrors("remove", *arguments) {
      val actualEnvName = envName ?: DEFAULT_ENV_NAME
      when {
        it.exitCode == 0 && it.stderr.startsWith("Removing environment") -> Result.success(RemoveResult.Removed)
        it.exitCode == 0 && it.stderr.isBlank() -> Result.success(RemoveResult.NotExists)
        it.stderr.startsWith("Environment `$actualEnvName` is not defined by project config") -> Result.success(RemoveResult.NotDefinedInConfig)
        it.stderr.startsWith("Cannot remove active environment") -> Result.success(RemoveResult.CantRemoveActiveEnvironment)
        else -> Result.failure(null)
      }
    }
  }

  /**
   * Returns details of the specified environments.
   *
   * @param envs A vararg parameter specifying the environment names to be displayed. If not provided, information for all environments is shown.
   * @param internal Optional parameter indicating whether to include internal environments. Defaults to null.
   * @return A [Result] containing:
   * - [Environments] if operation is successful.
   * - An error wrapped in [ExecException] if an execution failure occurs.
   */
  suspend fun show(vararg envs: String, internal: Boolean? = null): Result<Environments, ExecException> {
    val options = listOf(internal to "--internal").makeOptions()
    return executeAndHandleErrors("show", "--json", *options, *envs) { processOutput ->
      val output = processOutput.takeIf { it.exitCode == 0 }?.stdout
                   ?: return@executeAndHandleErrors Result.failure(null)

      val json = Json { ignoreUnknownKeys = true }
      val jsonOutput = json.parseToJsonElement(output)
      val environments = if (internal == true) jsonOutput.jsonObject
      else {
        // JSON mode always shows internal environments, and there is no flag to distinguish them
        jsonOutput.jsonObject.filterKeys { !it.startsWith("hatch-") }
      }

      val parsedEnvironments = environments.mapValues {
        json.decodeFromJsonElement<Environment>(it.value)
      }
      Result.success(parsedEnvironments)
    }
  }
}