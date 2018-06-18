// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

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
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.jetbrains.python.inspections.PyPackageRequirementsInspection
import com.jetbrains.python.packaging.*
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
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
const val PIPENV_PATH_SETTING: String = "PyCharm.Pipenv.Path"

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
  get() = sdkAdditionalData is PyPipEnvSdkAdditionalData
  set(value) {
    val oldData = sdkAdditionalData
    val newData = if (value) {
      when (oldData) {
        is PythonSdkAdditionalData -> PyPipEnvSdkAdditionalData(oldData)
        else -> PyPipEnvSdkAdditionalData()
      }
    }
    else {
      when (oldData) {
        is PyPipEnvSdkAdditionalData -> PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(this))
        else -> oldData
      }
    }
    val modificator = sdkModificator
    modificator.sdkAdditionalData = newData
    ApplicationManager.getApplication().runWriteAction { modificator.commitChanges() }
  }

/**
 * The user-set persisted path to the pipenv executable.
 */
var PropertiesComponent.pipEnvPath: @SystemDependent String?
  get() = getValue(PIPENV_PATH_SETTING)
  set(value) {
    setValue(PIPENV_PATH_SETTING, value)
  }

/**
 * Detects the pipenv executable in `$PATH`.
 */
fun detectPipEnvExecutable(): File? {
  val name = when {
    SystemInfo.isWindows -> "pipenv.exe"
    else -> "pipenv"
  }
  return PathEnvironmentVariableUtil.findInPath(name)
}

/**
 * Returns the configured pipenv executable or detects it automatically.
 */
fun getPipEnvExecutable(): File? =
  PropertiesComponent.getInstance().pipEnvPath?.let { File(it) } ?: detectPipEnvExecutable()

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
  val result = with(handler) {
    when {
      indicator != null -> {
        addProcessListener(PyPackageManagerImpl.IndicatedProcessOutputListener(indicator))
        runProcessWithProgressIndicator(indicator)
      }
      else ->
        runProcess()
    }
  }
  return with(result) {
    when {
      isCancelled ->
        throw RunCanceledByUserException()
      exitCode != 0 ->
        throw PyExecutionException("Error Running Pipenv", executable, args.asList(),
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
 * The URLs of package sources configured in the Pipfile.lock of the module associated with this SDK.
 */
val Sdk.pipFileLockSources: List<String>
  get() = parsePipFileLock()?.meta?.sources?.mapNotNull { it.url } ?:
          listOf(PIPENV_DEFAULT_SOURCE_URL)

/**
 * The list of requirements defined in the Pipfile.lock of the module associated with this SDK.
 */
val Sdk.pipFileLockRequirements: List<PyRequirement>?
  get() {
    fun toRequirements(packages: Map<String, PipFileLockPackage>): List<PyRequirement> =
      packages
        .asSequence()
        .filterNot { (_, pkg) -> pkg.editable ?: false }
        .flatMap { (name, pkg) -> packageManager.parseRequirements("$name${pkg.version ?: ""}").asSequence() }
        .toList()

    val pipFileLock = parsePipFileLock() ?: return null
    val packages = pipFileLock.packages?.let { toRequirements(it) } ?: emptyList()
    val devPackages = pipFileLock.devPackages?.let { toRequirements(it) } ?: emptyList()
    return packages + devPackages
  }

/**
 * A quick-fix for setting up the pipenv for the module of the current PSI element.
 */
class UsePipEnvQuickFix(sdk: Sdk?, module: Module) : LocalQuickFix {
  private val quickFixName = when {
    sdk != null && sdk.associatedModule != module -> "Fix Pipenv interpreter"
    else -> "Use Pipenv interpreter"
  }

  companion object {
    fun isApplicable(module: Module): Boolean = module.pipFile != null

    fun setUpPipEnv(project: Project, module: Module) {
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
      else {
        sdk.associateWithModule(module, false)
      }
      project.pythonSdk = sdk
      module.pythonSdk = sdk
    }
  }

  override fun getFamilyName() = quickFixName

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    // Invoke the setup later to escape the write action of the quick fix in order to show the modal progress dialog
    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed || module.isDisposed) return@invokeLater
      setUpPipEnv(project, module)
    }
  }
}

/**
 * A quick-fix for installing packages specified in Pipfile.lock.
 */
class PipEnvInstallQuickFix : LocalQuickFix {
  companion object {
    fun pipEnvInstall(project: Project, module: Module) {
      val sdk = module.pythonSdk ?: return
      if (!sdk.isPipEnv) return
      val listener = PyPackageRequirementsInspection.RunningPackagingTasksListener(module)
      val ui = PyPackageManagerUI(project, sdk, listener)
      ui.install(null, listOf("--dev"))
    }
  }

  override fun getFamilyName() = "Install requirements from Pipfile.lock"

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    pipEnvInstall(project, module)
  }
}

/**
 * Watches for edits in Pipfiles inside modules with a pipenv SDK set.
 */
class PipEnvPipFileWatcherComponent(val project: Project) : ProjectComponent {
  override fun projectOpened() {
    val editorFactoryListener = object : EditorFactoryListener {
      private val changeListenerKey = Key.create<DocumentListener>("Pipfile.change.listener")
      private val notificationActive = Key.create<Boolean>("Pipfile.notification.active")

      override fun editorCreated(event: EditorFactoryEvent) {
        if (!isPipFileEditor(event.editor)) return
        val listener = object : DocumentListener {
          override fun documentChanged(event: DocumentEvent?) {
            val module = event?.document?.virtualFile?.getModule(project) ?: return
            notifyPipFileChanged(module)
          }
        }
        with(event.editor.document) {
          addDocumentListener(listener)
          putUserData(changeListenerKey, listener)
        }
      }

      override fun editorReleased(event: EditorFactoryEvent) {
        val listener = event.editor.getUserData(changeListenerKey) ?: return
        event.editor.document.removeDocumentListener(listener)
      }

      private fun notifyPipFileChanged(module: Module) {
        if (module.getUserData(notificationActive) == true) return
        val what = when {
          module.pipFileLock == null -> "not found"
          else -> "out of date"
        }
        val title = "$PIP_FILE_LOCK for ${module.name} is $what"
        val content = "Run <a href='#lock'>pipenv lock</a> or <a href='#update'>pipenv update</a>"
        val notification = LOCK_NOTIFICATION_GROUP.createNotification(title, null, content,
                                                                      NotificationType.INFORMATION) { notification, event ->
          notification.expire()
          module.putUserData(notificationActive, null)
          FileDocumentManager.getInstance().saveAllDocuments()
          when (event.description) {
            "#lock" ->
              runPipEnvInBackground(module, listOf("lock"), "Locking $PIP_FILE")
            "#update" ->
              runPipEnvInBackground(module, listOf("update", "--dev"), "Updating Pipenv environment")
          }
        }
        module.putUserData(notificationActive, true)
        notification.whenExpired {
          module.putUserData(notificationActive, null)
        }
        notification.notify(project)
      }

      private fun runPipEnvInBackground(module: Module, args: List<String>, description: String) {
        val task = object : Task.Backgroundable(module.project, StringUtil.toTitleCase(description), true) {
          override fun run(indicator: ProgressIndicator) {
            val sdk = module.pythonSdk ?: return
            indicator.text = "$description..."
            try {
              runPipEnv(sdk, *args.toTypedArray())
            }
            catch (e: RunCanceledByUserException) {}
            catch (e: ExecutionException) {
              runInEdt {
                Messages.showErrorDialog(project, e.toString(), "Error Running Pipenv")
              }
            }
          }
        }
        ProgressManager.getInstance().run(task)
      }

      private fun isPipFileEditor(editor: Editor): Boolean {
        if (editor.project != project) return false
        val file = editor.document.virtualFile ?: return false
        if (file.name != PIP_FILE) return false
        val module = file.getModule(project) ?: return false
        if (module.pipFile != file) return false
        return module.pythonSdk?.isPipEnv == true
      }
    }
    EditorFactory.getInstance().addEditorFactoryListener(editorFactoryListener, project)
  }
}

private val Document.virtualFile: VirtualFile?
  get() = FileDocumentManager.getInstance().getFile(this)

private fun VirtualFile.getModule(project: Project): Module? =
  ModuleUtil.findModuleForFile(this, project)

private val LOCK_NOTIFICATION_GROUP = NotificationGroup("$PIP_FILE Watcher", NotificationDisplayType.STICKY_BALLOON, false)

private val Sdk.packageManager: PyPackageManager
  get() = PyPackageManagers.getInstance().forSdk(this)

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

private val Module.pipFileLock: VirtualFile?
  get() = baseDir?.findChild(PIP_FILE_LOCK)

private data class PipFileLock(@SerializedName("_meta") var meta: PipFileLockMeta?,
                               @SerializedName("default") var packages: Map<String, PipFileLockPackage>?,
                               @SerializedName("develop") var devPackages: Map<String, PipFileLockPackage>?)

private data class PipFileLockMeta(@SerializedName("sources") var sources: List<PipFileLockSource>?)

private data class PipFileLockSource(@SerializedName("url") var url: String?)

private data class PipFileLockPackage(@SerializedName("version") var version: String?,
                                      @SerializedName("editable") var editable: Boolean?)

