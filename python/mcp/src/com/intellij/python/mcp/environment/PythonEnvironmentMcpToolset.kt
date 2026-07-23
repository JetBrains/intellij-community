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
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.hatch.impl.sdk.HatchSdkFlavor
import com.intellij.python.pyproject.model.api.ModuleCreateInfo
import com.intellij.python.pyproject.model.api.autoConfigureSdkIfNeeded
import com.intellij.python.pyproject.model.api.getModuleInfo
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.createSdk
import com.jetbrains.python.sdk.findPythonSdk
import com.jetbrains.python.sdk.pipenv.PyPipEnvSdkFlavor
import com.jetbrains.python.sdk.poetry.PyPoetrySdkFlavor
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.pythonInterpreterAsync
import com.jetbrains.python.sdk.uv.UvSdkFlavor
import com.jetbrains.python.sdk.withSdkConfigurationLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable

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
    return buildResult(sdk, filePath)
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
    when (val moduleInfo = module.getModuleInfo()) {
      is ModuleCreateInfo.CreateSdkInfoWrapper -> when (val createSdkInfo = moduleInfo.createSdkInfo) {
        is CreateSdkInfo.ExistingEnv ->
          "PyCharm can attach the existing virtual environment detected for this module — $CONFIGURE_PYTHON_INTERPRETER_SUFFIX"
        is CreateSdkInfo.WillCreateEnv ->
          "PyCharm can create a new ${moduleInfo.toolId.id} environment — $CONFIGURE_PYTHON_INTERPRETER_SUFFIX"
        is CreateSdkInfo.WillInstallTool ->
          "PyCharm expects this project to use ${createSdkInfo.toolToInstall}, but ${createSdkInfo.toolToInstall} is not installed."
      }
      is ModuleCreateInfo.SameAs ->
        if (moduleInfo.parentModule.findPythonSdk() != null)
          "PyCharm can inherit the interpreter from parent module '${moduleInfo.parentModule.name}' — $CONFIGURE_PYTHON_INTERPRETER_SUFFIX"
        else null
      null -> null
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

    module.autoConfigureSdkIfNeeded()?.let { result ->
      val sdk = result.getOr { failure ->
        throw McpExpectedError("Failed to configure Python interpreter for '$filePath': ${failure.error.message}")
      }
      return buildResult(sdk, filePath)
    }

    val sdk = withSdkConfigurationLock(project) {
      module.findPythonSdk()?.let { return@withSdkConfigurationLock it }

      val moduleInfo = module.getModuleInfo()
                       ?: throw McpExpectedError(
                         "PyCharm has no interpreter suggestion for '$filePath'. " +
                         "Create a virtual environment manually (e.g. 'python -m venv .venv' or 'uv venv' if uv is installed) " +
                         "and re-call configure_python_interpreter to attach it.")

      val createSdkInfo = when (moduleInfo) {
        is ModuleCreateInfo.CreateSdkInfoWrapper -> moduleInfo.createSdkInfo
        is ModuleCreateInfo.SameAs -> {
          val parentSdk = moduleInfo.parentModule.findPythonSdk()
          if (parentSdk != null) {
            configurePythonSdk(project, module, parentSdk)
            return@withSdkConfigurationLock parentSdk
          }
          throw McpExpectedError(
            "'$filePath' is in a module that inherits its interpreter from '${moduleInfo.parentModule.name}', " +
            "which is not yet configured. Configure the parent module first by calling configure_python_interpreter " +
            "on a file inside '${moduleInfo.parentModule.name}'.")
        }
      }

      when (createSdkInfo) {
        is CreateSdkInfo.ExistingEnv, is CreateSdkInfo.WillCreateEnv -> {
          val newSdk = createSdkInfo.createSdk(module).getOr { failure ->
            throw McpExpectedError("Failed to configure Python interpreter for '$filePath': ${failure.error.message}")
          }
          configurePythonSdk(project, module, newSdk)
          newSdk
        }
        is CreateSdkInfo.WillInstallTool -> {
          val tool = createSdkInfo.toolToInstall
          throw McpExpectedError(
            "PyCharm needs to install '$tool' before it can configure an interpreter for '$filePath'. " +
            "This MCP tool does not install env-management tools. Install '$tool' manually " +
            "(e.g. 'brew install $tool', 'pipx install $tool', or 'pip install --user $tool') " +
            "and then re-call configure_python_interpreter.")
        }
      }
    }

    return buildResult(sdk, filePath)
  }

  private suspend fun resolveModule(project: Project, filePath: String): Module {
    val resolvedPath = project.resolveInProject(filePath, throwWhenOutside = false)
    val file = VirtualFileManager.getInstance().findFileByNioPath(resolvedPath)
               ?: throw McpExpectedError("File not found: $filePath")
    return readAction {
      ProjectFileIndex.getInstance(project).getModuleForFile(file)
    } ?: throw McpExpectedError("File is not part of any module: $filePath")
  }

  private suspend fun buildResult(sdk: Sdk, filePath: String): GetPythonEnvironmentResult {
    val interpreter = sdk.pythonInterpreterAsync()
    val env = interpreter.pythonEnvironment

    val environmentType = when (env) {
      is PythonEnvironment.Venv -> "venv"
      is PythonEnvironment.Conda -> "conda"
      is PythonEnvironment.SystemPython -> "system"
      null -> "unknown"
    }

    val executablePath = env?.pythonBinaryPath?.toString() ?: sdk.homePath
                         ?: throw McpExpectedError("Cannot determine Python executable path for: $filePath")

    val environmentPath = when (env) {
      is PythonEnvironment.Venv -> env.pythonHomePath.toString()
      is PythonEnvironment.Conda -> env.pythonHomePath.toString()
      else -> null
    }

    val packageManager = detectPackageManager(sdk, env)

    return GetPythonEnvironmentResult(
      pythonVersion = sdk.versionString ?: "unknown",
      environmentType = environmentType,
      executablePath = executablePath,
      environmentPath = environmentPath,
      packageManager = packageManager,
    )
  }

  /**
   * Picks the package manager that matches PyCharm's view of the SDK first (via the SDK additional data,
   * which is how PyCharm distinguishes poetry/hatch/uv envs from a plain venv on disk), then falls back to
   * inspecting the detected [PythonEnvironment] for SDKs not registered with one of those additional-data types.
   */
  private fun detectPackageManager(sdk: Sdk, env: PythonEnvironment?): String =
    // TODO: Still not nice, but at least it will detect remote interpreters properly
    when (sdk.pySdkAdditionalData.flavor) {
        PyPoetrySdkFlavor -> "poetry"
        HatchSdkFlavor -> "hatch"
        PyPipEnvSdkFlavor -> "pipenv"
        UvSdkFlavor -> "uv"
        else -> when (env) {
          is PythonEnvironment.Conda -> "conda"
          is PythonEnvironment.Venv -> if (env.config.containsKey("uv")) "uv" else "pip"
          is PythonEnvironment.SystemPython, null -> "unknown"
        }
    }

  @Serializable
  data class GetPythonEnvironmentResult(
    @property:McpDescription("Interpreter version string, e.g. 'Python 3.11.4'; null when no interpreter is configured")
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
