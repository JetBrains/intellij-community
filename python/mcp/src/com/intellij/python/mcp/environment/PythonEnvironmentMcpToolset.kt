// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.mcp.environment

import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue.FALSE
import com.intellij.mcpserver.annotations.McpToolHintValue.TRUE
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.pyproject.model.api.ModuleSdkState
import com.intellij.python.pyproject.model.api.SdkConfigurationResult
import com.intellij.python.pyproject.model.api.SdkForModuleConfigInstruction
import com.intellij.python.pyproject.model.api.configureSdkIfNeeded
import com.intellij.python.pyproject.model.api.getModuleSdkState
import com.intellij.python.pyproject.model.api.getPyProjectManager
import com.jetbrains.python.sdk.PythonInterpreter
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.findPythonSdk
import com.jetbrains.python.sdk.getVersion
import com.jetbrains.python.sdk.kindId
import com.jetbrains.python.sdk.pythonInterpreterAsync
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable
import kotlin.io.path.pathString

const val GET_PYTHON_ENVIRONMENT_TOOL: String = "get_python_environment"
const val CONFIGURE_PYTHON_INTERPRETER_TOOL: String = "configure_python_interpreter"

/** Tool names contributed by [PythonEnvironmentMcpToolset]; imported by the PyCharm bundled-skills provider. */
val PYTHON_ENVIRONMENT_TOOLS: Set<String> = setOf(GET_PYTHON_ENVIRONMENT_TOOL, CONFIGURE_PYTHON_INTERPRETER_TOOL)

/**
 * Suffix that tells the agent the configure tool can resolve the missing interpreter; the SKILL branches on it.
 * Keep this the single source of truth — `SKILL.md` and the tests match on this exact text.
 */
internal const val CONFIGURE_PYTHON_INTERPRETER_SUFFIX = "call configure_python_interpreter with the same --filePath."

class PythonEnvironmentMcpToolset : McpToolset {
  override fun isExperimental(): Boolean = false

  @McpTool(name = GET_PYTHON_ENVIRONMENT_TOOL)
  @McpToolHints(readOnlyHint = TRUE, idempotentHint = TRUE, openWorldHint = FALSE)
  @McpDescription(
    """
    Returns the Python environment configured for the given file.
    Reports the interpreter version, environment type (venv/conda/system), executable path, and package manager.
    Use this before running Python commands to know which executable and package manager to invoke.
    If the response contains a non-null "noInterpreterConfigured" field, no interpreter is set up for this module.
    Follow the instructions in that field to resolve it — call configure_python_interpreter when it says so.
    """
  )
  suspend fun getPythonEnvironment(
    @McpDescription("Absolute or project-relative path to the Python file (e.g. '/abs/path/file.py' or 'src/main.py').")
    filePath: String,
  ): GetPythonEnvironmentResult {
    val project = currentCoroutineContext().project
    val module = resolveModule(project, filePath)
    val sdk = module.findPythonSdk() ?: return GetPythonEnvironmentResult(
      noInterpreterConfigured = buildNoInterpreterMessage(module, filePath)
    )
    return buildResult(sdk.pythonInterpreterAsync(), filePath)
  }

  private suspend fun buildNoInterpreterMessage(module: Module, filePath: String): String {
    val base = "No Python interpreter configured for: $filePath."
    val hint = describeInterpreterSetupHint(module)
    return if (hint != null) "$base $hint" else base
  }

  /**
   * Returns a short, English suffix that describes what PyCharm would do next for [module] —
   * either "PyCharm can <action> — call configure_python_interpreter…" when the configure tool
   * would help, or "PyCharm expects this project to use <tool>, but <tool> is not installed."
   * when the project is wired for a tool that's missing on the system. Returns `null` when
   * PyCharm has no usable suggestion at all (and no tool to name).
   */
  private suspend fun describeInterpreterSetupHint(module: Module): String? =
    when (val moduleInfo = module.getModuleSdkState()) {
      is ModuleSdkState.HasSdk -> null
      is ModuleSdkState.NoSdk -> when (val r = moduleInfo.sdkConfigInstruction) {
        is SdkForModuleConfigInstruction.CreateSdkInfoWrapper -> {
          when (val createSdkInfo = r.createSdkInfoWithTool.createSdkInfo) {
            is CreateSdkInfo.ExistingEnv ->
              "PyCharm can attach the existing virtual environment detected for this module — $CONFIGURE_PYTHON_INTERPRETER_SUFFIX"
            is CreateSdkInfo.WillCreateEnv ->
              "PyCharm can create a new ${r.toolId.id} environment — $CONFIGURE_PYTHON_INTERPRETER_SUFFIX"
            is CreateSdkInfo.WillInstallTool ->
              "PyCharm expects this project to use ${createSdkInfo.toolToInstall}, but ${createSdkInfo.toolToInstall} is not installed."
          }
        }
        is SdkForModuleConfigInstruction.SameAs -> {
          if (r.parentModule.findPythonSdk() != null)
            "PyCharm can inherit the interpreter from parent module '${r.parentModule.name}' — $CONFIGURE_PYTHON_INTERPRETER_SUFFIX"
          else null
        }
        null -> null
      }
    }

  @McpTool(name = CONFIGURE_PYTHON_INTERPRETER_TOOL)
  @McpToolHints(readOnlyHint = FALSE, idempotentHint = TRUE, openWorldHint = FALSE)
  @McpDescription(
    """
    Configures a Python interpreter for the module containing the given file using PyCharm's own detection
    (existing .venv folder, or a fresh uv/poetry/hatch/pipenv venv when the corresponding tool is already installed).
    Call this only after get_python_environment reported that no interpreter is configured.
    This tool does NOT install env-management tools like uv or poetry; if PyCharm needs one, the call fails
    with the missing tool name so the agent can install it manually first and retry.
    """
  )
  suspend fun configurePythonInterpreter(
    @McpDescription("Absolute or project-relative path to a Python file inside the module whose interpreter should be configured.")
    filePath: String,
  ): GetPythonEnvironmentResult {
    val project = currentCoroutineContext().project
    val module = resolveModule(project, filePath)
    val pythonInterpreter = when (val r = module.configureSdkIfNeeded()) {
      null -> throw McpExpectedError(
        "PyCharm has no interpreter suggestion for '$filePath'. " +
        "Create a virtual environment manually (e.g. 'python -m venv .venv' or 'uv venv' if uv is installed) " +
        "and re-call configure_python_interpreter to attach it.")
      is SdkConfigurationResult.Configured -> r.sdk
      is SdkConfigurationResult.NotConfigured -> {
        throw McpExpectedError("Failed to configure Python interpreter for '$filePath': ${r.reason.message}")
      }
      is SdkConfigurationResult.ParentHasNoSdk -> {
        val parentModuleName = r.parentModule.name
        throw McpExpectedError(
          "'$filePath' is in a module that inherits its interpreter from '$parentModuleName', " +
          "which is not yet configured. Configure the parent module first by calling configure_python_interpreter " +
          "on a file inside '$parentModuleName'.")
      }
      is SdkConfigurationResult.ToolNotInstalled -> {
        val tool = r.tool.toolToInstall
        throw McpExpectedError(
          "PyCharm needs to install '${r.tool.toolToInstall}' before it can configure an interpreter for '$filePath'. " +
          "This MCP tool does not install env-management tools. Install '$tool' manually " +
          "(e.g. 'brew install $tool', 'pipx install $tool', or 'pip install --user $tool') " +
          "and then re-call configure_python_interpreter.")
      }
    }.pythonInterpreterAsync()
    return buildResult(pythonInterpreter, filePath)
  }

  private suspend fun resolveModule(project: Project, filePath: String): Module {
    val resolvedPath = project.resolveInProject(filePath, throwWhenOutside = false)
    val file = VirtualFileManager.getInstance().findFileByNioPath(resolvedPath)
               ?: throw McpExpectedError("File not found: $filePath")
    return readAction {
      ProjectFileIndex.getInstance(project).getModuleForFile(file)
    } ?: throw McpExpectedError("File is not part of any module: $filePath")
  }

  private suspend fun buildResult(interpreter: PythonInterpreter, filePath: String): GetPythonEnvironmentResult {
    val env = interpreter.pythonEnvironment
              ?: throw McpExpectedError("$filePath is broken")

    // The kind is named once, by the `id` of the provider that detected it. See `PythonEnvironmentProvider`.
    val environmentType = env.kindId ?: "unknown"
    val environmentPath = interpreter.pythonHomePath


    val packageManager = interpreter.getPyProjectManager().id.id

    return GetPythonEnvironmentResult(
      pythonVersion = interpreter.getVersion().successOrNull?.toString()?.let { "Python $it" } ?: "unknown",
      environmentType = environmentType,
      executablePath = env.pythonBinaryPath.pathString,
      environmentPath = environmentPath?.pathString,
      packageManager = packageManager,
    )
  }

  @Serializable
  data class GetPythonEnvironmentResult(
    @property:McpDescription("Interpreter version, e.g. '3.11.4'; null when no interpreter is configured")
    val pythonVersion: String? = null,
    @property:McpDescription("'venv', 'conda', 'system', or 'unknown'; null when no interpreter is configured")
    val environmentType: String? = null,
    @property:McpDescription("Absolute path to the Python binary to invoke; null when no interpreter is configured")
    val executablePath: String? = null,
    @property:McpDescription("Root of the virtual environment (venv prefix or conda prefix); null for system Python or unconfigured")
    val environmentPath: String? = null,
    @property:McpDescription("'pip', 'uv', 'poetry', 'hatch', 'pipenv', 'conda', or 'unknown'; null when no interpreter is configured")
    val packageManager: String? = null,
    @property:McpDescription("When non-null, no interpreter is configured for this module. Describes the current state and what action to take next.")
    val noInterpreterConfigured: String? = null,
  )
}
