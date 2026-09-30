// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.Platform
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.python.pytools.backend.ToolCommandSpec
import com.intellij.python.pytools.backend.ToolSearchPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ToolProbeResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

private const val TOOL_VERSION_PROBE_HELPER = "tool_version_probe.sh"
private const val PYTHON_PATH_OPTION = "--python"
private const val DETECT_ENVIRONMENTS_OPTION = "--detect-environments"
private const val SEARCH_PATH_KIND_ABSOLUTE = "absolute"
private const val SEARCH_PATH_KIND_ENV = "env"
private const val SEARCH_PATH_KIND_HOME = "home"

private val TOOL_PROBE_JSON = Json { ignoreUnknownKeys = true }

internal suspend fun TargetFileSystem.probeTargetTools(
  toolSpecs: List<ToolCommandSpec>,
  pythonPath: PathHolder.Target?,
  workingDir: Path?,
): PyResult<TargetProbeSnapshot> {
  if (platformAndRoot.platform == Platform.WINDOWS) {
    return PyResult.localizedError(PyBundle.message("python.sdk.target.tool.probe.windows.unsupported"))
  }

  val helper = PythonHelpersLocator.findPathInHelpersPossibleNull(TOOL_VERSION_PROBE_HELPER)
               ?: return PyResult.localizedError(PyBundle.message("python.sdk.target.tool.probe.helper.missing", TOOL_VERSION_PROBE_HELPER))
  val output = ExecService().execGetStdout(
    getBinaryToExec(PathHolder.Target("/bin/sh")),
    prepareArgs(helper, toolSpecs, pythonPath, workingDir),
    ExecOptions(timeout = 2.minutes),
  ).getOr { return it }
  val serializedSnapshot = try {
    TOOL_PROBE_JSON.decodeFromString<SerializedTargetProbeSnapshot>(output)
  }
  catch (_: SerializationException) {
    return PyResult.localizedError(PyBundle.message("python.sdk.target.tool.probe.output.invalid"))
  }

  // The helper writes "python":null only when pythonPath is null.
  val serializedPython = serializedSnapshot.python
  val pythonProbe = when {
    pythonPath == null && serializedPython == null -> null
    pythonPath != null && serializedPython != null -> TargetPythonProbeResult(pythonPath, serializedPython.toTargetPythonProbe())
    else -> error("The helper Python probe does not match the requested Python path: ${pythonPath?.toStringForUI()}")
  }
  val environments = serializedSnapshot.environments.map { environment ->
    // The helper always writes "<environment>/bin/python" here.
    check(environment.path.isNotBlank()) { "The helper wrote an environment with a blank path" }
    TargetEnvironmentProbe(PathHolder.Target(environment.path), environment.python.toTargetPythonProbe())
  }
  val tools = toolSpecs.mapNotNull { toolSpec ->
    val tool = serializedSnapshot.tools[toolSpec.toolName] ?: return@mapNotNull null
    // The helper writes a tool only when it found the tool.
    check(tool.path.isNotBlank()) { "The helper wrote the tool ${toolSpec.toolName} with a blank path" }
    toolSpec to ToolProbeResult(PathHolder.Target(tool.path), tool.versionOutput)
  }.toMap()
  return PyResult.success(TargetProbeSnapshot(serializedSnapshot.home, serializedSnapshot.shell, pythonProbe, environments, tools))
}

private fun TargetFileSystem.prepareArgs(
  helper: Path,
  toolSpecs: List<ToolCommandSpec>,
  pythonPath: PathHolder.Target?,
  workingDir: Path?,
): Args {
  val args = Args()
    .addLocalFile(helper)
    .addArgs(PYTHON_PATH_OPTION, pythonPath?.pathString.orEmpty())

  if (workingDir != null) {
    // Target file arguments are mapped through their parent. The "." keeps workingDir itself as the mapping root.
    args.addArgs(DETECT_ENVIRONMENTS_OPTION).addLocalFile(workingDir.resolve("."))
  }

  return args.addArgs(encodeToolProbeArgs(toolSpecs))
}

private fun TargetFileSystem.encodeToolProbeArgs(
  toolSpecs: List<ToolCommandSpec>,
): List<String> = buildList {
  for (toolSpec in toolSpecs) {
    val searchPaths = toolSpec.searchPathsFor(platformAndRoot.platform)
    add(toolSpec.toolName)
    add(searchPaths.size.toString())
    for (searchPath in searchPaths) {
      when (searchPath) {
        is ToolSearchPath.AbsolutePath -> {
          add(SEARCH_PATH_KIND_ABSOLUTE)
          add(searchPath.path)
        }
        is ToolSearchPath.RelativePath -> {
          add(SEARCH_PATH_KIND_ENV)
          add(searchPath.prefixEnvVar)
          add(searchPath.pathComponents.size.toString())
          addAll(searchPath.pathComponents)
        }
        is ToolSearchPath.RelativePathFromHome -> {
          add(SEARCH_PATH_KIND_HOME)
          add(searchPath.pathComponents.size.toString())
          addAll(searchPath.pathComponents)
        }
      }
    }
  }
}

@Serializable
private data class SerializedTargetProbeSnapshot(
  val shell: String,
  val home: String,
  val python: SerializedTargetPythonProbe? = null,
  val environments: List<SerializedTargetEnvironmentProbe> = emptyList(),
  val tools: Map<String, TargetToolProbe> = emptyMap(),
)

@Serializable
private data class SerializedTargetEnvironmentProbe(
  val path: FullPathOnTarget,
  val python: SerializedExecutablePython,
)

/**
 * The helper writes an environment only when its Python runs, thus no `isExecutable` field is necessary.
 */
@Serializable
private data class SerializedExecutablePython(
  val freeThreaded: Boolean,
  val versionOutput: String,
) {
  fun toTargetPythonProbe(): TargetPythonProbe.Executable = TargetPythonProbe.Executable(freeThreaded, versionOutput)
}

@Serializable
private data class SerializedTargetPythonProbe(
  val isExecutable: Boolean,
  val freeThreaded: Boolean? = null,
  val versionOutput: String? = null,
) {
  /**
   * `isExecutable=false` means that the helper cannot run the Python.
   */
  fun toTargetPythonProbe(): TargetPythonProbe = when {
    !isExecutable && freeThreaded == null && versionOutput == null -> TargetPythonProbe.NotExecutable
    isExecutable && freeThreaded != null && versionOutput != null -> TargetPythonProbe.Executable(freeThreaded, versionOutput)
    else -> error("The helper wrote an inconsistent Python probe: $this")
  }
}

@Serializable
private data class TargetToolProbe(
  val path: FullPathOnTarget,
  val versionOutput: String?,
)

internal sealed interface TargetPythonProbe {
  data object NotExecutable : TargetPythonProbe

  data class Executable(
    val freeThreaded: Boolean,
    val versionOutput: String,
  ) : TargetPythonProbe
}

internal data class TargetPythonProbeResult(
  val path: PathHolder.Target,
  val probe: TargetPythonProbe,
) {
  override fun toString(): String = "TargetPythonProbeResult(path=${path.toStringForUI()}, probe=$probe)"
}

internal data class TargetProbeSnapshot(
  val home: String,
  val shell: String,
  val python: TargetPythonProbeResult?,
  val environments: List<TargetEnvironmentProbe>,
  val tools: Map<ToolCommandSpec, ToolProbeResult<PathHolder.Target>>,
)

internal data class TargetEnvironmentProbe(
  val path: PathHolder.Target,
  val python: TargetPythonProbe.Executable,
) {
  override fun toString(): String = "TargetEnvironmentProbe(path=${path.toStringForUI()}, python=$python)"
}
