// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.AlreadyDisposedException
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.pythonSdk
import org.apache.tuweni.toml.Toml
import org.jetbrains.annotations.Nls
import com.intellij.notification.NotificationListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkCoroutineService
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.add.v2.PythonSelectableInterpreter
import com.jetbrains.python.sdk.findAmongRoots
import com.jetbrains.python.sdk.poetry.VersionType.Companion.getVersionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.toml.lang.psi.TomlKeyValue
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

const val PY_PROJECT_TOML: String = "pyproject.toml"
const val POETRY_LOCK: String = "poetry.lock"
const val POETRY_DEFAULT_SOURCE_URL: String = "https://pypi.org/simple"

val LOGGER = Logger.getInstance("#com.jetbrains.python.sdk.poetry")

internal suspend fun getPyProjectTomlForPoetry(virtualFile: VirtualFile): VirtualFile? =
  withContext(Dispatchers.IO) {
    readAction {
      try {
        Toml.parse(virtualFile.inputStream).getTable("tool.poetry")?.let { virtualFile }
      }
      catch (e: IOException) {
        LOGGER.info(e)
        null
      }
    }
  }


private suspend fun poetryLock(module: Module) = withContext(Dispatchers.IO) { findAmongRoots(module, POETRY_LOCK) }

/**
 * The PyProject.toml found in the main content root of the module.
 */
@Internal
suspend fun pyProjectToml(module: Module): VirtualFile? = withContext(Dispatchers.IO) { findAmongRoots(module, PY_PROJECT_TOML) }


/**
 * Watches for edits in PyProjectToml inside modules with a poetry SDK set.
 */
class PoetryPyProjectTomlWatcher : EditorFactoryListener {
  private val changeListenerKey = Key.create<DocumentListener>("PyProjectToml.change.listener")
  private val notificationActive = Key.create<Boolean>("PyProjectToml.notification.active")
  private suspend fun content(): @Nls String = if (getPoetryVersion()?.let { it < "1.1.1" } == true) {
    PyBundle.message("python.sdk.poetry.pip.file.notification.content")
  }
  else {
    PyBundle.message("python.sdk.poetry.pip.file.notification.content.without.updating")
  }

  override fun editorCreated(event: EditorFactoryEvent) {
    val project = event.editor.project ?: return
    service<PythonSdkCoroutineService>().cs.launch {
      if (!isPyProjectTomlEditor(event.editor)) return@launch
      val listener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          service<PythonSdkCoroutineService>().cs.launch {
            try {
              val document = event.document

              val module = document.virtualFile?.let { getModule(it, project) } ?: return@launch
              // TODO: Should we remove listener when a sdk is changed to non-poetry sdk?
              //                    if (!isPoetry(module.project)) {
              //                        with(document) {
              //                            putUserData(notificationActive, null)
              //                            val listener = getUserData(changeListenerKey) ?: return
              //                            removeDocumentListener(listener)
              //                            putUserData(changeListenerKey, null)
              //                            return
              //                        }
              //                    }
              if (FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
                notifyPyProjectTomlChanged(module)
              }

            }
            catch (_: AlreadyDisposedException) {
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

  private suspend fun notifyPyProjectTomlChanged(module: Module) {
    if (module.getUserData(notificationActive) == true) return
    @Suppress("DialogTitleCapitalization") val title = when (poetryLock(module)) {
      null -> PyBundle.message("python.sdk.poetry.pip.file.lock.not.found")
      else -> PyBundle.message("python.sdk.poetry.pip.file.lock.out.of.date")
    }
    val notification = LOCK_NOTIFICATION_GROUP.createNotification(title, content(), NotificationType.INFORMATION).setListener(
      NotificationListener { notification, event ->
        FileDocumentManager.getInstance().saveAllDocuments()
        when (event.description) {
          "#lock" ->
            runPoetryInBackground(module, listOf("lock"), PyBundle.message("python.sdk.poetry.pip.file.notification.locking"))
          "#noupdate" ->
            runPoetryInBackground(module, listOf("lock", "--no-update"),
                                  PyBundle.message("python.sdk.poetry.pip.file.notification.locking.without.updating"))
          "#update" ->
            runPoetryInBackground(module, listOf("update"), PyBundle.message("python.sdk.poetry.pip.file.notification.updating"))
        }
        notification.expire()
        module.putUserData(notificationActive, null)
      })
    module.putUserData(notificationActive, true)
    notification.whenExpired {
      module.putUserData(notificationActive, null)
    }
    notification.notify(module.project)
  }

  private suspend fun isPyProjectTomlEditor(editor: Editor): Boolean {
    val file = editor.document.virtualFile ?: return false
    if (file.name != PY_PROJECT_TOML) return false
    val project = editor.project ?: return false
    val module = getModule(file, project) ?: return false
    val sdk = module.pythonSdk ?: return false
    if (!sdk.isPoetry) return false
    return pyProjectToml(module) == file
  }

  private val Document.virtualFile: VirtualFile?
    get() = FileDocumentManager.getInstance().getFile(this)

  private suspend fun getModule(file: VirtualFile, project: Project): Module? = withContext(Dispatchers.IO) {
    ModuleUtil.findModuleForFile(file, project)
  }

  private val LOCK_NOTIFICATION_GROUP by lazy { NotificationGroupManager.getInstance().getNotificationGroup("pyproject.toml Watcher") }
}

/**
 * This class represents a post-startup activity for PyProjectToml files in a project.
 * It finds valid python versions in PyProjectToml files and saves them in PyProjectTomlPythonVersionsService.
 */
private class PoetryPyProjectTomlPostStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val modulesRoots = PythonSdkUpdater.getModuleRoots(project)
    for (module in modulesRoots) {
      val tomlFile = withContext(Dispatchers.IO) {
        module.findChild(PY_PROJECT_TOML)?.let { getPyProjectTomlForPoetry(it) }
      } ?: continue
      val versionString = poetryFindPythonVersionFromToml(tomlFile, project) ?: continue

      PoetryPyProjectTomlPythonVersionsService.instance.setVersion(module, versionString)
      addDocumentListener(tomlFile, project, module)
    }
  }


  /**
   * Adds a document listener to a given toml file.
   * Updates PyProjectTomlPythonVersionsService map if needed.
   *
   * @param tomlFile The VirtualFile representing the toml file.
   * @param project The Project in which the toml file exists.
   * @param module The VirtualFile representing the module.
   */
  private suspend fun addDocumentListener(tomlFile: VirtualFile, project: Project, module: VirtualFile) {
    readAction {
      tomlFile.findDocument()?.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          service<PythonSdkCoroutineService>().cs.launch {
            val newVersion = poetryFindPythonVersionFromToml(tomlFile, project) ?: return@launch
            val oldVersion = PoetryPyProjectTomlPythonVersionsService.instance.getVersionString(module)
            if (oldVersion != newVersion) {
              PoetryPyProjectTomlPythonVersionsService.instance.setVersion(module, newVersion)
            }
          }
        }
      }, PoetryPyProjectTomlPythonVersionsService.instance)
    }
  }
}

/**
 * Finds the Python version string specified in the toml file.
 *
 * @param tomlFile The VirtualFile representing the toml file.
 * @param project The Project in which the toml file exists.
 * @return The Python version specified in the toml file, or null if not found.
 */
@Internal
suspend fun poetryFindPythonVersionFromToml(tomlFile: VirtualFile, project: Project): String? {
  val versionElement = readAction {
    val tomlPsiFile = tomlFile.findPsiFile(project) ?: return@readAction null
    (PsiTreeUtil.collectElements(tomlPsiFile, object : PsiElementFilter {
      override fun isAccepted(element: PsiElement): Boolean {
        if (element is TomlKeyValue) {
          if (element.key.text == "python") {
            return true
          }
        }
        return false
      }
    }).firstOrNull() as? TomlKeyValue)?.value?.text
  }

  return versionElement?.substring(1, versionElement.length - 1)
}

/**
 * Service class that stores Python versions specified in a pyproject.toml file.
 */
@Internal
@Service
class PoetryPyProjectTomlPythonVersionsService : Disposable {
  private val modulePythonVersions: ConcurrentMap<VirtualFile, PoetryPythonVersion> = ConcurrentHashMap()

  companion object {
    val instance: PoetryPyProjectTomlPythonVersionsService
      get() = service()
  }

  fun setVersion(moduleFile: VirtualFile, stringVersion: String) {
    modulePythonVersions[moduleFile] = PoetryPythonVersion(stringVersion)
  }

  fun getVersionString(moduleFile: VirtualFile): String = getVersion(moduleFile).stringVersion

  fun validateSdkVersions(moduleFile: VirtualFile, sdks: List<Sdk>): List<Sdk> =
    sdks.filter { getVersion(moduleFile).isValid(it.versionString) }

  fun validateInterpretersVersions(moduleFile: VirtualFile, interpreters: Flow<List<PythonSelectableInterpreter>>): Flow<List<PythonSelectableInterpreter>> {
    val version = getVersion(moduleFile)
    return interpreters.map { list -> list.filter { version.isValid(it.languageLevel) } }
  }

  private fun getVersion(moduleFile: VirtualFile): PoetryPythonVersion =
    modulePythonVersions[moduleFile] ?: PoetryPythonVersion("")

  override fun dispose() {}
}

@Internal
enum class VersionType {
  LESS,
  LESS_OR_EQUAL,
  EQUAL,
  MORE_OR_EQUAL,
  MORE;

  companion object {
    fun String.getVersionType(): VersionType? =
      when (this) {
        "<" -> LESS
        "<=" -> LESS_OR_EQUAL
        "=", "" -> EQUAL
        "^", ">=" -> MORE_OR_EQUAL
        ">" -> MORE
        else -> null
      }
  }
}


private fun getDefaultValueByType(type: VersionType): Int? =
  when (type) {
    VersionType.LESS, VersionType.MORE_OR_EQUAL -> 0
    VersionType.LESS_OR_EQUAL, VersionType.MORE -> 20
    VersionType.EQUAL -> null
  }

private fun Triple<Int, Int?, Int?>.compare(versionTriple: Pair<VersionType, Triple<Int, Int?, Int?>>): Int {
  val type = versionTriple.first
  val version = versionTriple.second

  return this.first.compareTo(version.first).takeIf { it != 0 }
         ?: this.second?.compareTo(version.second ?: (getDefaultValueByType(type) ?: this.second ?: 0)).takeIf { it != 0 }
         ?: this.third?.compareTo(version.third ?: (getDefaultValueByType(type) ?: this.third ?: 0)).takeIf { it != 0 }
         ?: 0
}

@Internal
data class PoetryPythonVersion(val stringVersion: String) {
  val descriptions: List<Pair<VersionType, Triple<Int, Int?, Int?>>>

  init {
    descriptions = parseVersion(stringVersion)
  }

  private fun parseVersion(versionString: String): List<Pair<VersionType, Triple<Int, Int?, Int?>>> {
    if (versionString.isEmpty()) return emptyList()
    val versionParts = versionString.split(",")
    val result = mutableListOf<Pair<VersionType, Triple<Int, Int?, Int?>>>()

    for (part in versionParts) {
      val firstDigit = part.indexOfFirst { it.isDigit() }
      if (firstDigit == -1) continue
      val type = part.substring(0, firstDigit).trim().getVersionType() ?: continue
      val version = part.substring(firstDigit).trim()
      val versionTriple = PoetryVersionValue.create(version).getOrNull()?.version
      versionTriple?.let { result.add(Pair(type, versionTriple)) }
    }
    return result
  }

  fun isValid(versionString: String?): Boolean {
    if (versionString.isNullOrBlank()) return false
    val baseInterpreterVersion = PoetryVersionValue.create(versionString).getOrNull()?.version ?: return false
    if (baseInterpreterVersion.first < 3 || baseInterpreterVersion.first == 3 && baseInterpreterVersion.second?.let { it < 6 } == true) return false
    for (description in descriptions) {
      val type = description.first
      val compareResult = baseInterpreterVersion.compare(description)
      when (type) {
        VersionType.LESS -> if (compareResult >= 0) return false
        VersionType.LESS_OR_EQUAL -> if (compareResult > 0) return false
        VersionType.EQUAL -> if (compareResult != 0) return false
        VersionType.MORE_OR_EQUAL -> if (compareResult < 0) return false
        VersionType.MORE -> if (compareResult <= 0) return false
      }
    }
    return true
  }

  fun isValid(languageLevel: LanguageLevel): Boolean {
    val languageLevelString = languageLevel.toString()
    return isValid(languageLevelString)
  }
}

@JvmInline
value class PoetryVersionValue private constructor(val version: Triple<Int, Int?, Int?>) {
  companion object {
    fun create(versionString: String): Result<PoetryVersionValue> {
      try {
        val integers = versionString.split(".").map { it.toInt() }
        return when (integers.size) {
          1 -> Result.success(PoetryVersionValue(Triple(integers[0], null, null)))
          2 -> Result.success(PoetryVersionValue(Triple(integers[0], integers[1], null)))
          3 -> Result.success(PoetryVersionValue(Triple(integers[0], integers[1], integers[2])))
          else -> Result.failure(NumberFormatException())
        }
      }
      catch (e: NumberFormatException) {
        return Result.failure(e)
      }
    }
  }
}