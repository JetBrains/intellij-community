// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageManagers
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.sdk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

const val PIP_FILE: String = "Pipfile"
const val PIP_FILE_LOCK: String = "Pipfile.lock"
const val PIPENV_PATH_SETTING: String = "PyCharm.Pipenv.Path"

/**
 * The Pipfile found in the main content root of the module.
 */
@Internal
suspend fun pipFile(module: Module) = withContext(Dispatchers.IO) { findAmongRoots(module, PIP_FILE) }

/**
 * Resolves and returns the list of Python requirements from the Pipfile.lock of the SDK's associated module.
 *
 * @return A list of [PyRequirement] parsed from the Pipfile.lock, or `null` if the file cannot be accessed or parsed.
 */
@Internal
suspend fun pipFileLockRequirements(sdk: Sdk): List<PyRequirement>? {
  return sdk.pipFileLock()?.let { getPipFileLockRequirements(it, sdk.packageManager) }
}

/**
 * Watches for edits in Pipfiles inside modules with a pipenv SDK set.
 */
internal class PipEnvPipFileWatcher : EditorFactoryListener {
  private val changeListenerKey = Key.create<DocumentListener>("Pipfile.change.listener")
  private val notificationActive = Key.create<Boolean>("Pipfile.notification.active")

  override fun editorCreated(event: EditorFactoryEvent) {
    service<PythonSdkCoroutineService>().cs.launch {
      val project = event.editor.project
      if (project == null || !isPipFileEditor(event.editor)) return@launch
      val listener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          val document = event.document
          val module = document.virtualFile?.getModule(project) ?: return
          if (FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
            service<PythonSdkCoroutineService>().cs.launch {
              notifyPipFileChanged(module)
            }
          }
        }
      }
      with(event.editor.document) {
        addDocumentListener(listener)
        putUserData(changeListenerKey, listener)
      }
    }
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    val listener = event.editor.getUserData(changeListenerKey) ?: return
    event.editor.document.removeDocumentListener(listener)
  }

  private enum class PipEnvEvent(val description: String) {
    LOCK("#lock"),
    UPDATE("#update")
  }

  private suspend fun notifyPipFileChanged(module: Module) {
    if (module.getUserData(notificationActive) == true) return
    val title = when {
      pipFileLock(module) == null -> PyBundle.message("python.sdk.pipenv.pip.file.lock.not.found")
      else -> PyBundle.message("python.sdk.pipenv.pip.file.lock.out.of.date")
    }
    val content = PyBundle.message("python.sdk.pipenv.pip.file.notification.content")
    val notification = withContext(Dispatchers.EDT) { LOCK_NOTIFICATION_GROUP.createNotification(title, content, NotificationType.INFORMATION) }
      .setListener(NotificationListener { notification, event ->
        notification.expire()
        module.putUserData(notificationActive, null)
        runInEdt { FileDocumentManager.getInstance().saveAllDocuments() }
        when (event.description) {
          PipEnvEvent.LOCK.description -> runPipEnvInBackground(module, listOf("lock"), PyBundle.message("python.sdk.pipenv.pip.file.notification.locking"))
          PipEnvEvent.UPDATE.description -> runPipEnvInBackground(module, listOf("update", "--dev"), PyBundle.message("python.sdk.pipenv.pip.file.notification.updating"))
        }
      })
    module.putUserData(notificationActive, true)
    notification.whenExpired {
      module.putUserData(notificationActive, null)
    }
    notification.notify(module.project)
  }

  private fun runPipEnvInBackground(module: Module, args: List<String>, @ProgressTitle description: String) {
    service<PythonSdkCoroutineService>().cs.launch {
      withBackgroundProgress(module.project, description) {
        val sdk = module.pythonSdk ?: return@withBackgroundProgress
        runPipEnv(sdk.associatedModulePath?.let { Path.of(it) }, *args.toTypedArray()).onFailure {
          if (it is ExecutionException) {
            withContext(Dispatchers.EDT) {
              showSdkExecutionException(sdk, it, PyBundle.message("python.sdk.pipenv.execution.exception.error.running.pipenv.message"))
            }
          }
          else {
            throw it
          }
        }

        withContext(Dispatchers.Default) {
          PythonSdkUtil.getSitePackagesDirectory(sdk)?.refresh(true, true)
          sdk.associatedModuleDir?.refresh(true, false)
        }
      }
    }
  }

  private suspend fun isPipFileEditor(editor: Editor): Boolean {
    val file = editor.document.virtualFile ?: return false
    if (file.name != PIP_FILE) return false
    val project = editor.project ?: return false
    val module = file.getModule(project) ?: return false
    if (pipFile(module) != file) return false
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

private suspend fun getPipFileLockRequirements(virtualFile: VirtualFile, packageManager: PyPackageManager): List<PyRequirement>? {
  @RequiresBackgroundThread
  fun toRequirements(packages: Map<String, PipFileLockPackage>): List<PyRequirement> =
    packages
      .asSequence()
      .filterNot { (_, pkg) -> pkg.editable ?: false }
      // TODO: Support requirements markers (PEP 496), currently any packages with markers are ignored due to PY-30803
      .filter { (_, pkg) -> pkg.markers == null }
      .flatMap { (name, pkg) -> packageManager.parseRequirements("$name${pkg.version ?: ""}") }
      .toList()

  val pipFileLock = parsePipFileLock(virtualFile).getOrNull() ?: return null
  val packages = pipFileLock.packages?.let { withContext(Dispatchers.IO) { toRequirements(it) } } ?: emptyList()
  val devPackages = pipFileLock.devPackages?.let { withContext(Dispatchers.IO) { toRequirements(it) } } ?: emptyList()
  return packages + devPackages
}

private val gson = Gson()

private suspend fun parsePipFileLock(virtualFile: VirtualFile): Result<PipFileLock> =
  withContext(Dispatchers.IO) {
    val text = readAction {
      FileDocumentManager.getInstance().getDocument(virtualFile)?.text
    }
    try {
      Result.success(gson.fromJson(text, PipFileLock::class.java))
    }
    catch (e: JsonSyntaxException) {
      Result.failure(e)
    }
  }

private suspend fun Sdk.pipFileLock(): VirtualFile? = withContext(Dispatchers.IO) {
  associatedModulePath?.let { StandardFileSystems.local().findFileByPath(it)?.findChild(PIP_FILE_LOCK) }
}

private suspend fun pipFileLock(module: Module): VirtualFile? = withContext(Dispatchers.IO) { findAmongRoots(module, PIP_FILE_LOCK) }

private data class PipFileLock(
  @SerializedName("_meta") var meta: PipFileLockMeta?,
  @SerializedName("default") var packages: Map<String, PipFileLockPackage>?,
  @SerializedName("develop") var devPackages: Map<String, PipFileLockPackage>?,
)

private data class PipFileLockMeta(@SerializedName("sources") var sources: List<PipFileLockSource>?)

private data class PipFileLockSource(@SerializedName("url") var url: String?)

private data class PipFileLockPackage(
  @SerializedName("version") var version: String?,
  @SerializedName("editable") var editable: Boolean?,
  @SerializedName("hashes") var hashes: List<String>?,
  @SerializedName("markers") var markers: String?,
)

@TestOnly
fun getPipFileLockRequirementsSync(lockRequirements: VirtualFile): List<PyRequirement>? = runBlockingMaybeCancellable {
  @RequiresBackgroundThread
  fun toRequirements(packages: Map<String, PipFileLockPackage>): List<PyRequirement> =
    packages
      .asSequence()
      .filterNot { (_, pkg) -> pkg.editable == true }
      .filter { (_, pkg) -> pkg.markers == null }
      .flatMap { (name, pkg) -> PyRequirementParser.fromText("$name${pkg.version ?: ""}") }
      .toList()

  val pipFileLock = parsePipFileLock(lockRequirements).getOrNull() ?: return@runBlockingMaybeCancellable null
  val packages = pipFileLock.packages?.let { withContext(Dispatchers.IO) { toRequirements(it) } } ?: emptyList()
  val devPackages = pipFileLock.devPackages?.let { withContext(Dispatchers.IO) { toRequirements(it) } } ?: emptyList()
  return@runBlockingMaybeCancellable packages + devPackages
}