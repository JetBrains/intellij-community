// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.features

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.features.PyRunToolProvider.Companion.forSdk
import org.jetbrains.annotations.ApiStatus

/**
 * The parameters of the tool this configuration runs with, or `null` when the interpreter runs it by itself.
 *
 * The same decision is used for Run and the pydevd debugger through [com.jetbrains.python.run.PythonCommandLineState.startProcess].
 * The debugpy debuggee uses it too, although its launch bypasses that method (PY-93398).
 */
@ApiStatus.Internal
suspend fun AbstractPythonRunConfiguration<*>.resolveRunToolParameters(sdk: Sdk): PyRunToolParameters? {
  if (!enableRunTool) return null
  val provider = forSdk(sdk) ?: return null
  if (!useRunTool(sdk)) return null
  // Resolved here rather than in the tool: only the configuration knows whether it runs a bare script.
  val inlineScriptTarget = asBareScriptConfiguration()?.inlineScriptTarget
  val parameters = provider.getRunToolParameters(sdk, inlineScriptTarget)
  PyRunToolUsageCollector.logRun(project, PyRunToolIds.idOf(provider), inlineScriptTarget != null)
  return parameters
}

/**
 * How a process on the machine of the interpreter starts [python] through a run tool: [command] in place of [python],
 * with [envs] added to its variables.
 */
@ApiStatus.Internal
class PyRunToolLaunch(val command: List<FullPathOnTarget>, val envs: Map<String, String>)

/**
 * The launch of [python] through this tool for a process that the machine of the interpreter spawns rather than the
 * IDE, such as the debuggee of the debugpy launcher. [python] and the paths in the result are paths on that machine.
 * For an interpreter of the IDE host that machine is the host itself.
 * The command is the one `PythonScripts.applyRunToolAsync` builds for Run.
 */
@ApiStatus.Internal
fun PyRunToolParameters.launchOnInterpreterMachine(python: FullPathOnTarget): PyRunToolLaunch =
  PyRunToolLaunch(buildList {
    addAll(listOf(this@launchOnInterpreterMachine.exe.toStringOnRemoteMachine()))
    addAll(this@launchOnInterpreterMachine.args)
    addAll(listOfNotNull(python.takeIf { this@launchOnInterpreterMachine.includeOriginalExe }))
  }, envs)

internal fun AbstractPythonRunConfiguration<*>.useRunTool(sdk: Sdk): Boolean {
  val explicit = this.useRunTool
  if (explicit != null) return explicit
  val provider = forSdk(sdk) ?: return false
  return provider.initialToolState
}

/**
 * The tool this configuration will actually be run through, or `null` when it will be run by the interpreter itself.
 * Backs everything the run configuration says about that tool.
 */
internal fun AbstractPythonRunConfiguration<*>.activeRunToolData(): PyRunToolData? {
  if (!enableRunTool) return null
  val sdk = sdk ?: return null
  val provider = forSdk(sdk) ?: return null
  return if (useRunTool(sdk)) provider.runToolData else null
}