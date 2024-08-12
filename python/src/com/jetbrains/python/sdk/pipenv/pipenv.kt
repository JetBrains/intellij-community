// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.ProcessOutput
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.inspections.PyPackageRequirementsInspection
import com.jetbrains.python.packaging.*
import com.jetbrains.python.sdk.*
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.nio.file.Path
import javax.swing.Icon

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
  set(value) = setCorrectTypeSdk(this, PyPipEnvSdkAdditionalData::class.java, value)

/**
 * The user-set persisted a path to the pipenv executable.
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

fun suggestedSdkName(basePath: @NlsSafe String): @NlsSafe String = "Pipenv (${PathUtil.getFileName(basePath)})"

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
  val projectPath = newProjectPath ?: module?.basePath ?: project?.basePath ?: return null
  val task = object : Task.WithResult<String, ExecutionException>(project, PyBundle.message("python.sdk.setting.up.pipenv.title"), true) {
    override fun compute(indicator: ProgressIndicator): String {
      indicator.isIndeterminate = true
      val pipEnv = setupPipEnv(FileUtil.toSystemDependentName(projectPath), python, installPackages)
      return  VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(pipEnv))?.toString() ?: FileUtil.join(pipEnv, "bin", "python")
    }
  }
  return createSdkByGenerateTask(task, existingSdks, null, projectPath, suggestedSdkName(projectPath))?.apply {
    // FIXME: multi module project support - associate with module path
    setAssociationToPath(projectPath)
    isPipEnv = true
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
  val projectPath = sdk.associatedModulePath ?: throw PyExecutionException(
    PyBundle.message("python.sdk.pipenv.execution.exception.no.project.message"),
    "Pipenv", emptyList(), ProcessOutput())
  return runPipEnv(projectPath, *args)
}

/**
 * Runs the configured pipenv for the specified project path.
 */
fun runPipEnv(projectPath: @SystemDependent String, vararg args: String): String {
  val executable = getPipEnvExecutable()?.toPath() ?: throw PyExecutionException(
    PyBundle.message("python.sdk.pipenv.execution.exception.no.pipenv.message"),
    "pipenv", emptyList(), ProcessOutput())
  @Suppress("DialogTitleCapitalization")
  return runCommand(executable, Path.of(projectPath), PyBundle.message("python.sdk.pipenv.execution.exception.error.running.pipenv.message"), *args)
}

/**
 * The URLs of package sources configured in the Pipfile.lock of the module associated with this SDK.
 */
val Sdk.pipFileLockSources: List<String>
  get() = parsePipFileLock()?.meta?.sources?.mapNotNull { it.url } ?: listOf(PIPENV_DEFAULT_SOURCE_URL)

/**
 * The list of requirements defined in the Pipfile.lock of the module associated with this SDK.
 */
val Sdk.pipFileLockRequirements: List<PyRequirement>?
  get() {
    return pipFileLock?.let { getPipFileLockRequirements(it, packageManager) }
  }

/**
 * A quick-fix for setting up the pipenv for the module of the current PSI element.
 */
class PipEnvAssociationQuickFix : LocalQuickFix {
  private val quickFixName = PyBundle.message("python.sdk.pipenv.quickfix.use.pipenv.name")

  override fun getFamilyName() = quickFixName

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    module.pythonSdk?.setAssociationToModule(module)
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

  override fun getFamilyName() = PyBundle.message("python.sdk.install.requirements.from.pipenv.lock")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    pipEnvInstall(project, module)
  }
}

/**
 * Watches for edits in Pipfiles inside modules with a pipenv SDK set.
 */
class PipEnvPipFileWatcher : EditorFactoryListener {
  private val changeListenerKey = Key.create<DocumentListener>("Pipfile.change.listener")
  private val notificationActive = Key.create<Boolean>("Pipfile.notification.active")

  override fun editorCreated(event: EditorFactoryEvent) {
    val project = event.editor.project
    if (project == null || !isPipFileEditor(event.editor)) return
    val listener = object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        val document = event.document
        val module = document.virtualFile?.getModule(project) ?: return
        if (FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
          notifyPipFileChanged(module)
        }
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
    val title = when {
      module.pipFileLock == null -> PyBundle.message("python.sdk.pipenv.pip.file.lock.not.found")
      else -> PyBundle.message("python.sdk.pipenv.pip.file.lock.out.of.date")
    }
    val content = PyBundle.message("python.sdk.pipenv.pip.file.notification.content")
    val notification = LOCK_NOTIFICATION_GROUP.createNotification(title, content, NotificationType.INFORMATION)
      .setListener(NotificationListener { notification, event ->
        notification.expire()
        module.putUserData(notificationActive, null)
        FileDocumentManager.getInstance().saveAllDocuments()
        when (event.description) {
          "#lock" -> runPipEnvInBackground(module, listOf("lock"), PyBundle.message("python.sdk.pipenv.pip.file.notification.locking"))
          "#update" -> runPipEnvInBackground(module, listOf("update", "--dev"), PyBundle.message(
            "python.sdk.pipenv.pip.file.notification.updating"))
        }
      })
    module.putUserData(notificationActive, true)
    notification.whenExpired {
      module.putUserData(notificationActive, null)
    }
    notification.notify(module.project)
  }

  private fun runPipEnvInBackground(module: Module, args: List<String>, @ProgressTitle description: String) {
    val task = object : Task.Backgroundable(module.project, description, true) {
      override fun run(indicator: ProgressIndicator) {
        val sdk = module.pythonSdk ?: return
        indicator.text = "$description..."
        try {
          runPipEnv(sdk, *args.toTypedArray())
        }
        catch (_: RunCanceledByUserException) {
        }
        catch (e: ExecutionException) {
          showSdkExecutionException(sdk, e, PyBundle.message("python.sdk.pipenv.execution.exception.error.running.pipenv.message"))
        }
        finally {
          PythonSdkUtil.getSitePackagesDirectory(sdk)?.refresh(true, true)
          sdk.associatedModuleDir?.refresh(true, false)
        }
      }
    }
    ProgressManager.getInstance().run(task)
  }

  private fun isPipFileEditor(editor: Editor): Boolean {
    val file = editor.document.virtualFile ?: return false
    if (file.name != PIP_FILE) return false
    val project = editor.project ?: return false
    val module = file.getModule(project) ?: return false
    if (module.pipFile != file) return false
    return module.pythonSdk?.isPipEnv == true
  }
}

private val Document.virtualFile: VirtualFile?
  get() = FileDocumentManager.getInstance().getFile(this)

private fun VirtualFile.getModule(project: Project): Module? =
  ModuleUtil.findModuleForFile(this, project)

private val LOCK_NOTIFICATION_GROUP = Cancellation.forceNonCancellableSectionInClassInitializer {
  NotificationGroupManager.getInstance().getNotificationGroup("Pipfile Watcher")
}

private val Sdk.packageManager: PyPackageManager
  get() = PyPackageManagers.getInstance().forSdk(this)


@TestOnly
fun getPipFileLockRequirements(virtualFile: VirtualFile, packageManager: PyPackageManager): List<PyRequirement>? {
  fun toRequirements(packages: Map<String, PipFileLockPackage>): List<PyRequirement> =
    packages
      .asSequence()
      .filterNot { (_, pkg) -> pkg.editable ?: false }
      // TODO: Support requirements markers (PEP 496), currently any packages with markers are ignored due to PY-30803
      .filter { (_, pkg) -> pkg.markers == null }
      .flatMap { (name, pkg) -> packageManager.parseRequirements("$name${pkg.version ?: ""}").asSequence() }
      .toList()

  val pipFileLock = parsePipFileLock(virtualFile) ?: return null
  val packages = pipFileLock.packages?.let { toRequirements(it) } ?: emptyList()
  val devPackages = pipFileLock.devPackages?.let { toRequirements(it) } ?: emptyList()
  return packages + devPackages
}

private fun Sdk.parsePipFileLock(): PipFileLock? {
  // TODO: Log errors if Pipfile.lock is not found
  val file = pipFileLock ?: return null
  return parsePipFileLock(file)
}

private fun parsePipFileLock(virtualFile: VirtualFile): PipFileLock? {
  val text = ReadAction.compute<String, Throwable> { FileDocumentManager.getInstance().getDocument(virtualFile)?.text }
  return try {
    Gson().fromJson(text, PipFileLock::class.java)
  }
  catch (e: JsonSyntaxException) {
    // TODO: Log errors
    return null
  }
}

val Sdk.pipFileLock: VirtualFile?
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
                                      @SerializedName("editable") var editable: Boolean?,
                                      @SerializedName("hashes") var hashes: List<String>?,
                                      @SerializedName("markers") var markers: String?)
