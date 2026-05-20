package com.intellij.python.pyproject.model.internal.autoImportBridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectAware
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectListener
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectReloadContext
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.project.stateStore
import com.intellij.python.pyproject.model.internal.PY_PROJECT_SYSTEM_ID
import com.intellij.python.pyproject.model.internal.PyProjectScopeService
import com.intellij.python.pyproject.model.internal.notifyModelRebuilt
import com.intellij.python.pyproject.model.internal.pyProjectToml.walkFileSystemNoTomlContent
import com.intellij.python.pyproject.model.internal.pyProjectToml.walkFileSystemWithTomlContent
import com.intellij.python.pyproject.model.internal.workspaceBridge.collectExcludedPaths
import com.intellij.python.pyproject.model.internal.workspaceBridge.rebuildProjectModel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.Topic
import com.intellij.util.ui.EDT
import com.jetbrains.python.sdk.baseDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
@VisibleForTesting
class PyExternalSystemProjectAware private constructor(
  private val project: Project,
) : ExternalSystemProjectAware {

  override val projectId: ExternalSystemProjectId =
    ExternalSystemProjectId(PY_PROJECT_SYSTEM_ID, project.stateStore.projectBasePath.pathString)


  /**
   * Project might have several "attached" modules outside its base path.
   */
  @RequiresBackgroundThread
  private fun getRootPaths(): Set<Path> {
    // guessPath doesn't work: it returns first module path
    val projectRootDir = project.stateStore.projectBasePath
    val modulePaths = project.modules.asSequence().mapNotNull { it.baseDir?.toNioPath() }
    return computeMinimalRoots(sequenceOf(projectRootDir) + modulePaths)
  }

  @get:RequiresBackgroundThread
  override val settingsFiles: Set<String>
    get() {
      if (EDT.isCurrentThreadEdt() && ApplicationManager.getApplication().isUnitTestMode) {
        // Some tests are broken and access it from EDT.
        // Since `@RequiresBackgroundThread` doesn't work for Kotlin, we can't check it in advance.
        // This part will be rewritten soon anyway, so for now enjoy workaround
        log.warn("Access from EDT, settingsFiles are empty")
        return emptySet()
      }
      return runBlockingMaybeCancellable {
        // We do not need file content: only names here.
        val fsInfo = walkFileSystemNoTomlContent(getRootPaths()).getOr {
          // Dir can't be accessed
          log.trace(it.error)
          return@runBlockingMaybeCancellable emptySet()
        }
        return@runBlockingMaybeCancellable fsInfo.rawTomlFiles.map { it.pathString }.toSet()
      }
    }

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(PROJECT_AWARE_TOPIC, listener)
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    project.service<PyProjectScopeService>().scope.launchTracked {
      reloadProjectImpl()
    }
  }

  @ApiStatus.Internal
  @VisibleForTesting
  suspend fun reloadProjectImpl() {
    edtWriteAction {
      // We might get stale files otherwise
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    val projectRoots = withContext(Dispatchers.IO) {
      getRootPaths()
    }

    project.messageBus.syncAndPreloadPublisher(PROJECT_AWARE_TOPIC).apply {
      try {
        log.debug {
          "Reload project called"
        }
        this.onProjectReloadStart()
        val excludedPaths = collectExcludedPaths(project)
        val files = walkFileSystemWithTomlContent(projectRoots, excludedPaths).getOr {
          if (log.isTraceEnabled) {
            log.warn("Can't access $projectRoots", it.error)
          }
          this.onProjectReloadFinish(ExternalSystemRefreshStatus.FAILURE)
          return
        }
        log.debug {
          "Files found: ${files.tomlFiles.keys.joinToString(", ")}"
        }

        rebuildProjectModel(project, files)
        this.onProjectReloadFinish(ExternalSystemRefreshStatus.SUCCESS)
        // Even though we have no entities, we still "rebuilt" the model, time to configure SDK
        notifyModelRebuilt(project)
      }
      catch (e: CancellationException) {
        this.onProjectReloadFinish(ExternalSystemRefreshStatus.CANCEL)
        throw e
      }
      catch (e: Exception) {
        this.onProjectReloadFinish(ExternalSystemRefreshStatus.FAILURE)
        throw e
      }
    }
  }


  companion object {
    /**
     * [project] can't be default, be sure to check it
     */
    @ApiStatus.Internal
    @VisibleForTesting
    fun create(project: Project): PyExternalSystemProjectAware {
      assert(!project.isDefault) { "Default project not supported" }
      return PyExternalSystemProjectAware(project)
    }
  }
}


@Topic.ProjectLevel
private val PROJECT_AWARE_TOPIC: Topic<ExternalSystemProjectListener> =
  Topic(ExternalSystemProjectListener::class.java, Topic.BroadcastDirection.NONE)


private val log = fileLogger()

/**
 * Returns the minimal set of [paths] such that no element is a descendant of another.
 */
@ApiStatus.Internal
@VisibleForTesting
internal fun computeMinimalRoots(paths: Sequence<Path>): Set<Path> {
  val fsCaseSensitivity = HashMap<FileSystem, Boolean>()
  return paths
    .map { it.normalize() }
    .distinct()
    .map { it to it.sortKey(fsCaseSensitivity) } // path to key to be used as sort
    .sortedBy { it.second }
    .map { it.first } // With deep sort first we always have parent before us
    .fold(mutableListOf<Path>()) { roots, p ->
      if (roots.isEmpty() || !p.startsWith(roots.last())) {
        roots.add(p)
      }
      roots
    }.toSet()
}

/**
 * [Path] sort is broken by default, what we need is deep traversal, so '/foo', '/foo/bar', '/quax'
 */
private fun Path.sortKey(fsCaseSensitivity: HashMap<FileSystem, Boolean>): String {
  val sep = fileSystem.separator
  val s = toString()
  val withSep = if (s.endsWith(sep)) s else s + sep // Windows root has separator, other dirs do not
  val caseSensitive = fsCaseSensitivity.getOrPut(fileSystem) {
    isCaseSensitive()
  }
  return if (caseSensitive) withSep else withSep.uppercase() // To ignore case
}

private fun Path.isCaseSensitive(): Boolean =
  resolve("A") != resolve("a")
