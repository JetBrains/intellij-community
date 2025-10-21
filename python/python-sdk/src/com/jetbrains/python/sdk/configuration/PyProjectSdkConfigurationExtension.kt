// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyToolUIInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue

/**
 * Used on directory opening with an attempt to configure suitable Python interpreter
 * (mentioned below as sdk configurator).
 *
 * Used with an attempt to suggest suitable Python interpreter
 * or try setup and register it in case of headless mode if no interpreter is specified.
 */
@ApiStatus.Internal
interface PyProjectSdkConfigurationExtension {
  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<PyProjectSdkConfigurationExtension> = ExtensionPointName.create("Pythonid.projectSdkConfigurationExtension")

    @JvmStatic
    @RequiresBackgroundThread
    fun findForModule(module: Module): CreateSdkInfo? = runBlockingMaybeCancellable {
      EP_NAME.extensionsIfPointIsRegistered.firstNotNullOfOrNull { ext -> ext.checkEnvironmentAndPrepareSdkCreator(module) }
    }
  }

  val toolInfo: PyToolUIInfo

  /**
   * Discovers whether this extension can provide a Python SDK for the given module and prepares a creator for it.
   *
   * This function is executed on a background thread and may perform I/O-intensive checks such as
   * reading project files (for example, pyproject.toml, Pipfile, requirements.txt, environment.yml), probing the
   * file system, or invoking external tools (poetry/hatch/pipenv/uv/etc.). No SDK must be created or registered here.
   * Instead, the method returns a [CreateSdkInfo] descriptor that encapsulates:
   * - user-facing labels (intentionName) and tool metadata (toolInfo), and
   * - a suspendable sdkCreator that will create and register the SDK when executed by the caller
   *   (see [PyProjectSdkConfiguration.setSdkUsingCreateSdkInfo]).
   *
   * Return value semantics:
   * - Existing environment found: return a CreateSdkInfo.ExistingEnv whose creator simply registers the discovered SDK.
   * - No environment yet, but can be created: return a CreateSdkInfo.WillCreateEnv whose creator performs the creation
   *   (and optional user confirmation) and registers the SDK.
   * - Tool is not applicable, or configuration cannot proceed (missing binaries, incompatible project, errors): return null.
   *   Implementations are responsible for showing any user-facing error notifications when they decide to return null.
   *
   * The default ordering prefers existing environments over newly created ones; see CreateSdkInfo.compareTo.
   *
   * @param module module to inspect and derive configuration from
   * @return descriptor to create/register a suitable SDK, or null if this extension cannot configure the project
   */
  @CheckReturnValue
  suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module): CreateSdkInfo?

  /**
   * Returns this extension as a [PyProjectTomlConfigurationExtension] when a tool supports configuring with
   * pyproject.toml, or null otherwise.
   *
   * Callers that need to skip pyproject.toml validation should do it using
   * [PyProjectTomlConfigurationExtension.createSdkWithoutPyProjectTomlChecks].
   */
  fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension?
}
