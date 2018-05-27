// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.sdk.*
import icons.PythonIcons
import org.jetbrains.annotations.SystemDependent
import java.io.File
import javax.swing.Icon

/**
 * @author vlan
 */

const val PIP_FILE: String = "Pipfile"
const val PIP_FILE_LOCK: String = "Pipfile.lock"
const val PIPENV_DEFAULT_SOURCE_URL: String = "https://pypi.org/simple"

// TODO: Provide a special icon for pipenv
val PIPENV_ICON: Icon = PythonIcons.Python.PythonClosed

/**
 * The Pipfile found in the main content root of the module.
 */
val Module.pipFile: VirtualFile?
  get() =
    baseDir?.findChild(PIP_FILE)

/**
 * Tells if the SDK was added as a pipenv.
 */
var Sdk.isPipEnv: Boolean
  get() = (sdkAdditionalData as? PythonSdkAdditionalData)?.isPipEnv ?: false
  set(value) {
    getOrCreateAdditionalData().isPipEnv = value
  }

/**
 * Finds the pipenv executable in `$PATH`.
 */
fun getPipEnvExecutable(): File? {
  val name = when {
    SystemInfo.isWindows -> "pipenv.exe"
    else -> "pipenv"
  }
  return PathEnvironmentVariableUtil.findInPath(name)
}

/**
 * Sets up the pipenv environment under the modal progress window.
 *
 * The pipenv is associated with the first valid object from this list:
 *
 * 1. New project specified by [newProjectPath]
 * 2. Existing module specified by [module]
 * 3. Existing project specified by [project]
 *
 * @return the SDK for pipenv, not stored in the SDK table yet.
 */
fun setupPipEnvSdkUnderProgress(project: Project?,
                                module: Module?,
                                existingSdks: List<Sdk>,
                                newProjectPath: String?,
                                python: String?,
                                installPackages: Boolean): Sdk? {
  val projectPath = newProjectPath ?:
                    module?.basePath ?:
                    project?.basePath ?:
                    return null
  val task = object : Task.WithResult<String, ExecutionException>(project, "Setting Up Pipenv Environment", true) {
    override fun compute(indicator: ProgressIndicator): String {
      indicator.isIndeterminate = true
      val pipEnv = setupPipEnv(FileUtil.toSystemDependentName(projectPath), python, installPackages)
      return PythonSdkType.getPythonExecutable(pipEnv) ?: FileUtil.join(pipEnv, "bin", "python")
    }
  }
  val suggestedName = "Pipenv (${PathUtil.getFileName(projectPath)})"
  return createSdkByGenerateTask(task, existingSdks, null, projectPath, suggestedName)?.apply {
    isPipEnv = true
    associateWithModule(module, newProjectPath != null)
  }
}

/**
 * Sets up the pipenv environment for the specified project path.
 *
 * @return the path to the pipenv environment.
 */
fun setupPipEnv(projectPath: @SystemDependent String, python: String?, installPackages: Boolean): @SystemDependent String {
  when {
    installPackages -> {
      val pythonArgs = if (python != null) listOf("--python", python) else emptyList()
      val command = pythonArgs + listOf("install", "--dev")
      runPipEnv(projectPath, *command.toTypedArray())
    }
    python != null ->
      runPipEnv(projectPath, "--python", python)
    else ->
      runPipEnv(projectPath, "run", "python", "-V")
  }
  return runPipEnv(projectPath, "--venv").trim()
}

/**
 * Runs the configured pipenv for the specified Pipenv SDK with the associated project path.
 */
fun runPipEnv(sdk: Sdk, vararg args: String): String {
  val projectPath = sdk.associatedModulePath ?:
                    throw PyExecutionException("Cannot find the project associated with this Pipenv environment",
                                               "Pipenv", emptyList(), ProcessOutput())
  return runPipEnv(projectPath, *args)
}

/**
 * Runs the configured pipenv for the specified project path.
 */
fun runPipEnv(projectPath: @SystemDependent String, vararg args: String): String {
  val executable = getPipEnvExecutable()?.path ?:
                   throw PyExecutionException("Cannot find Pipenv", "pipenv", emptyList(), ProcessOutput())

  val command = listOf(executable) + args
  val commandLine = GeneralCommandLine(command).withWorkDirectory(projectPath)
  val handler = CapturingProcessHandler(commandLine)
  val indicator = ProgressManager.getInstance().progressIndicator
  val result = if (indicator != null) {
    handler.runProcessWithProgressIndicator(indicator)
  }
  else {
    // TODO: Show the output at the progress dialog
    handler.runProcess()
  }
  with(result) {
    return when {
      isCancelled ->
        throw RunCanceledByUserException()
      exitCode != 0 ->
        throw PyExecutionException("Cannot run Python from Pipenv", executable, args.asList(),
                                   stdout, stderr, exitCode, emptyList())
      else -> stdout
    }
  }
}

/**
 * Detects and sets up pipenv SDK for a module with Pipfile.
 */
fun detectAndSetupPipEnv(project: Project?, module: Module?, existingSdks: List<Sdk>): Sdk? {
  if (module?.pipFile == null || getPipEnvExecutable() == null) {
    return null
  }
  return setupPipEnvSdkUnderProgress(project, module, existingSdks, null, null, false)
}

/**
 * URLs of package sources configured in the Pipfile.lock of the module associated with this SDK.
 */
val Sdk.pipFileLockSources: List<String>
  get() = parsePipFileLock()?.meta?.sources?.mapNotNull { it.url } ?:
          listOf(PIPENV_DEFAULT_SOURCE_URL)

/**
 * A quick-fix for setting up the pipenv for the module of the current PSI element.
 */
class UsePipEnvQuickFix : LocalQuickFix {
  companion object {
    fun isApplicable(module: Module): Boolean = module.pipFile != null

    fun setUpPipEnv(project: Project, module: Module) {
      if (project.isDisposed || module.isDisposed) {
        return
      }
      val sdksModel = ProjectSdksModel().apply {
        reset(project)
      }
      val existingSdks = sdksModel.sdks.filter { it.sdkType is PythonSdkType }
      // XXX: Should we show an error message on exceptions and on null?
      val newSdk = setupPipEnvSdkUnderProgress(project, module, existingSdks, null, null, false) ?: return
      val existingSdk = existingSdks.find { it.isPipEnv && it.homePath == newSdk.homePath }
      val sdk = existingSdk ?: newSdk
      if (sdk == newSdk) {
        SdkConfigurationUtil.addSdk(newSdk)
      }
      project.pythonSdk = sdk
      module.pythonSdk = sdk
    }
  }

  override fun getFamilyName() = "Use Pipenv interpreter"

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    // Invoke the setup later to escape the write action of the quick fix in order to show the modal progress dialog
    ApplicationManager.getApplication().invokeLater {
      setUpPipEnv(project, module)
    }
  }
}

private fun Sdk.parsePipFileLock(): PipFileLock? {
  // TODO: Log errors if Pipfile.lock is not found
  val file = pipFileLock ?: return null
  val text = ReadAction.compute<String, Throwable> { FileDocumentManager.getInstance().getDocument(file)?.text }
  return try {
    Gson().fromJson(text, PipFileLock::class.java)
  }
  catch (e: JsonSyntaxException) {
    // TODO: Log errors
    return null
  }
}

private val Sdk.pipFileLock: VirtualFile?
  get() =
    associatedModulePath?.let { StandardFileSystems.local().findFileByPath(it)?.findChild(PIP_FILE_LOCK) }

private data class PipFileLock(@SerializedName("_meta") var meta: PipFileLockMeta?)

private data class PipFileLockMeta(@SerializedName("sources") var sources: List<PipFileLockSource>?)

private data class PipFileLockSource(@SerializedName("url") var url: String?)

