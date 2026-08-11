// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.features

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.provider.localEel
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.TargetFileSystem
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.sdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Feature flag for Python "Run with …" tool integration.
 */
internal val enableRunTool: Boolean get() = Registry.`is`("run.with.py.tool")

@JvmInline
@ApiStatus.Internal
value class PyRunToolId(@param:NonNls val value: String)

internal object PyRunToolIds {
  @JvmStatic
  fun idOf(provider: PyRunToolProvider): String = provider.runToolData.id.value
}

/**
 * Metadata describing a concrete Python "Run with …" tool option.
 *
 * - id: a unique identifier of the tool.
 * - name: user‑visible action/configuration name (localized).
 * - group: user‑visible group name under which the action is shown in UI (localized).
 * - idForStatistics: identifier for FUS statistics (should not change to avoid breaking statistics).
 */
@ApiStatus.Internal
data class PyRunToolData(
  @param:NonNls val id: PyRunToolId,
  @param:Nls val name: String,
  @param:Nls val group: String,
  @param:NonNls val idForStatistics: String = id.value,
)

/**
 * Represents the parameters needed to run a Python tool or executable.
 *
 * @property exe The path to the Python executable or script.
 * @property args A list of arguments to be passed to the executable.
 * @property envs A map of environment variables to be used during execution.
 */
@ApiStatus.Internal
data class PyRunToolParameters(
  val exe: String,
  val args: List<String>,
  val envs: Map<String, String>,
  val includeOriginalExe: Boolean = true,
)

/**
 * Represents a provider for Python "Run with …" tools. This interface defines
 * the metadata, parameters, and state management for tools available under the
 * "Run with" menu in Python environments.
 */
@ApiStatus.Internal
interface PyRunToolProvider {

  /**
   * Represents the metadata of a specific Python "Run with …" tool option.
   * This property provides details such as the unique identifier, user-visible name,
   * and UI group for the tool associated with a `PyRunToolProvider`.
   */
  val runToolData: PyRunToolData

  /**
   * Represents the parameters required to configure and run a Python tool.
   * This includes the path to the executable and a list of associated arguments.
   */
  suspend fun getRunToolParameters(sdk: Sdk): PyRunToolParameters

  /**
   * Represents the initial state of the tool, determining whether it is enabled or not by default.
   */
  val initialToolState: Boolean

  /**
   * Checks if the tool is available for the given SDK.
   *
   * @param sdk the SDK to check the availability for
   * @return true if the tool is available for the specified SDK, false otherwise
   */
  fun isAvailable(sdk: Sdk): Boolean

  companion object {
    @JvmField
    val EP: ExtensionPointName<PyRunToolProvider> = ExtensionPointName.create("Pythonid.pyRunToolProvider")

    @JvmStatic
    fun forSdk(sdk: Sdk): PyRunToolProvider? = EP.extensionList.firstOrNull { it.isAvailable(sdk) }
  }
}

/**
 * Base class for [PyRunToolProvider] implementations that are bound to a specific [PyFlavorData] subtype.
 */
@ApiStatus.Internal
@PyInternalExecApi
abstract class PySdkRunToolProvider<T : PyFlavorData, U : PythonSdkFlavor<T>>(
  private val flavorClass: Class<U>,
) : PyRunToolProvider {
  override fun isAvailable(sdk: Sdk): Boolean = flavorClass.isInstance(sdk.sdkFlavor)

  override suspend fun getRunToolParameters(sdk: Sdk): PyRunToolParameters {
    val additionalData = sdk.pySdkAdditionalData
    val fileSystem = when (additionalData) {
      is PyTargetAwareAdditionalData -> additionalData.targetEnvironmentConfiguration?.let {
        TargetFileSystem(it, PythonLanguageRuntimeConfiguration())
      } ?: localEel.toFileSystem()
      else -> localEel.toFileSystem()
    }

    @Suppress("UNCHECKED_CAST") // Checked by isAvailable and the flavor's data contract
    val flavorAndData = additionalData.flavorAndData as PyFlavorAndData<T, U>
    return getRunToolParameters(requireNotNull(sdk.homePath), flavorAndData.data, fileSystem)
  }

  protected abstract suspend fun <P : PathHolder> getRunToolParameters(
    sdkHome: FullPathOnTarget,
    flavorData: T,
    fileSystem: FileSystem<P>,
  ): PyRunToolParameters
}