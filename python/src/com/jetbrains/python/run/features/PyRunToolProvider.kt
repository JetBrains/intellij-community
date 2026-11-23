// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.features

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.python.pytools.common.FusId
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.toFileSystem
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

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
 * - label: short user-visible name, shown next to a configuration that runs with the tool.
 * - icon: tool icon, badged onto the icon of a configuration that runs with the tool.
 * - idForStatistics: identifier for FUS statistics (should not change to avoid breaking statistics).
 */
@ApiStatus.Internal
data class PyRunToolData(
  @param:NonNls val id: PyRunToolId,
  @param:Nls val name: String,
  @param:Nls val group: String,
  @param:Nls @get:Nls val label: String,
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
  internal val exe: PathHolder,
  internal val args: List<String>,
  internal val envs: Map<String, String>,
  internal val includeOriginalExe: Boolean = true,
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
   *
   * [inlineScriptTarget] is set only when the run should be treated as a PEP 723 script
   * (see [com.jetbrains.python.run.PyBareScriptConfiguration]); a tool that has no notion of that ignores it and
   * behaves as it does for any other run.
   */
  suspend fun getRunToolParameters(interpreter: PythonInterpreter, inlineScriptTarget: Path?): PyRunToolParameters

  /**
   * Represents the initial state of the tool, determining whether it is enabled or not by default.
   */
  val initialToolState: Boolean

  /** Whether this tool can run Python in the environment of [interpreter]. */
  fun isAvailable(interpreter: PythonInterpreter): Boolean

  companion object {
    @JvmField
    val EP: ExtensionPointName<PyRunToolProvider> = ExtensionPointName.create("Pythonid.pyRunToolProvider")

    /** The tool that can run Python in the environment of [interpreter], or `null` when no tool can. */
    @JvmStatic
    fun forInterpreter(interpreter: PythonInterpreter): PyRunToolProvider? = EP.extensionList.firstOrNull { it.isAvailable(interpreter) }
  }
}

/**
 * Base class for a [PyRunToolProvider] of the tool that manages the environment of an interpreter, see
 * [com.intellij.python.sdk.common.PyInterpreterRef.manager].
 */
@ApiStatus.Internal
@PyInternalExecApi
abstract class PyInterpreterRunToolProvider : PyRunToolProvider {
  /** The id of the tool that manages the environments this provider runs, see [com.intellij.python.pytools.backend.PyTool]. */
  protected abstract val manager: FusId

  override fun isAvailable(interpreter: PythonInterpreter): Boolean = interpreter.ref.manager == manager

  override suspend fun getRunToolParameters(interpreter: PythonInterpreter, inlineScriptTarget: Path?): PyRunToolParameters =
    getRunToolParameters(interpreter, interpreter.toFileSystem(), inlineScriptTarget)

  /** The parameters to run Python of [interpreter] through this tool on the machine of [fileSystem]. */
  protected abstract suspend fun <P : PathHolder> getRunToolParameters(
    interpreter: PythonInterpreter,
    fileSystem: FileSystem<P>,
    inlineScriptTarget: Path?,
  ): PyRunToolParameters
}