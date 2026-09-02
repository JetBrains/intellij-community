// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.workspaceModel.ide.toPath
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.project.stateStore
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.notifyModelRebuilt
import com.intellij.python.pyproject.model.internal.pyProjectToml.findPyProjectTomlWithContent
import com.intellij.python.pyproject.model.internal.workspaceBridge.collectExcludedPaths
import com.intellij.python.pyproject.model.internal.workspaceBridge.rebuildProjectModel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Builds the `pyproject.toml` project model and keeps it up to date.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PyProjectModelSyncService(private val project: Project, private val scope: CoroutineScope) : Disposable {
  private val m = Any()

  init {
    assert(!project.isDefault) { "Default project not supported" }
  }

  private var session: Session? = null

  /**
   * The roots of the last rebuild. The VFS listener reads them to drop a change of another project.
   *
   * [getRootPaths] needs a background thread, and the listener must stay cheap, so the value is cached here.
   * Before the first rebuild it holds the project base path only.
   */
  @Volatile
  private var knownRoots: Set<Path> = emptySet()

  @get:TestOnly
  internal val initialized: Boolean get() = synchronized(m) { session != null }

  /**
   * Builds the model and starts to track the project for a change. Does nothing if already started.
   * Method is synchronized. You can always [stop] it, so does [dispose].
   */
  fun start(): Unit = synchronized(m) {
    if (session != null) {
      log.info("PyProject sync already started")
      return@synchronized
    }
    val disposable = Disposer.newDisposable("PyProjectModelSyncService")
    Disposer.register(this, disposable)
    knownRoots = setOf(project.stateStore.projectBasePath)
    val requests = Channel<RebuildRequest>(Channel.UNLIMITED)
    // Both trackers subscribe before the job starts, so no change of the wait window is lost.
    // The channel is unlimited, hence a request that arrives before the first build waits in it.
    subscribeToPyProjectTomlChanges(disposable, { knownRoots }) { requests.trySend(it) }
    val wsmTrackerJob = scope.createWsmTracker(project) { unExcluded -> requests.trySend(RebuildRequest(unExcluded)) }
    val job = scope.launch {
      awaitVfsAndJpsModel()
      rebuildSafely(loadProjectRoots = true)
      consumeRequests(requests)
    }
    session = Session(disposable, job, wsmTrackerJob)
    log.info("PyProject sync started")
  }

  /** Stops the tracking started by [start]. Does nothing if already stopped. Method is synchronized. */
  fun stop(): Unit = synchronized(m) {
    val session = this.session ?: return@synchronized
    log.info("PyProject sync stopped")
    session.job.cancel()
    session.wsmTrackerJob.cancel()
    Disposer.dispose(session.disposable)
    this.session = null
  }

  override fun dispose() {
    stop()
  }

  /**
   * Waits until the VFS knows the content roots, and until the workspace model matches the JPS files.
   *
   * `UnindexedFilesScanner` scans the content roots first and schedules `InitialVfsRefreshService` after that.
   * The wait therefore also covers the scan, which is the pass that loads the content roots into the VFS.
   * The JPS wait answers the caveat of PY-91841: the model build must not race the JPS project load.
   *
   * The wait does **not** cover the whole project tree. `ProjectRootManagerEx.markRootsForRefresh` returns the
   * module content roots plus the library and SDK roots, and the project base path is one of them only when a
   * module already covers it. [loadProjectRootsIntoVfs] closes that gap before the first build.
   *
   * Both waits are bounded, see [awaitOrWarn].
   */
  private suspend fun awaitVfsAndJpsModel() {
    awaitOrWarn("the initial VFS refresh") {
      project.serviceAsync<InitialVfsRefreshService>().awaitInitialVfsRefreshFinished()
    }
    awaitOrWarn("the JPS model") {
      // The cast is the sanctioned way to reach this API. `JpsProjectLoadingManager` is deprecated in its favour,
      // and `PythonSdkAdditionalDataMigrationActivity` does the same.
      (project.workspaceModel as WorkspaceModelInternal).awaitSynchronizationWithJpsModel()
    }
  }

  /**
   * Runs [wait] and gives up after [AWAIT_TIMEOUT], with a warning that names [what].
   *
   * A wait must never park the sync for the life of the project. `InitialVfsRefreshService` completes its
   * deferred only when something calls `scheduleInitialVfsRefresh` or `runInitialVfsRefresh`, and only
   * `UnindexedFilesScanner` calls them. A host that runs no scanning pass would otherwise build no model at
   * all, and [start] would still report the sync as started (PY-91841).
   *
   * A build without the wait stays correct. [loadProjectRootsIntoVfs] loads the tree itself, and
   * [rebuildProjectModel] repeats the build when the JPS load changes the module set at the same time.
   * The wait only saves the work of a build that a later build would repeat.
   */
  private suspend fun awaitOrWarn(what: String, wait: suspend () -> Unit) {
    log.debug { "Waiting for $what" }
    if (withTimeoutOrNull(AWAIT_TIMEOUT) { wait() } == null) {
      log.warn("$what did not finish in $AWAIT_TIMEOUT. The pyproject.toml model is built without it.")
    }
    else {
      log.debug { "Got $what" }
    }
  }

  /**
   * Rebuilds the model once per batch of [requests].
   *
   * A batch is drained after a short delay, so a burst of VFS events costs one rebuild.
   * [rebuildProjectModel] holds a mutex of its own, hence two rebuilds never overlap.
   */
  private suspend fun consumeRequests(requests: Channel<RebuildRequest>) {
    for (firstRequest in requests) {
      delay(DEBOUNCE)
      val directoriesToLoad = LinkedHashSet<VirtualFile>()
      var request: RebuildRequest? = firstRequest
      while (request != null) {
        directoriesToLoad.addAll(request.directoriesToLoad)
        request = requests.tryReceive().getOrNull()
      }
      loadSubtreesIntoVfs(directoriesToLoad, collectExcludedPaths(project))
      rebuildSafely()
    }
  }

  /**
   * Runs [rebuildNow] and stops an error here.
   *
   * The whole sync is one job. An exception that leaves this method ends that job, and the request channel then
   * grows with no consumer. No later change of a `pyproject.toml` would reach the model, and [start] would still
   * report the sync as started. Only a reopen of the project would repair it.
   */
  // The catch must be broad here. This is the last point that can keep the sync alive, and the model reads a
  // file, a TOML parser and the workspace model, so the kind of a failure is not known in advance.
  @Suppress("PyExceptionTooBroad")
  private suspend fun rebuildSafely(loadProjectRoots: Boolean = false) {
    try {
      if (loadProjectRoots) {
        loadProjectRootsIntoVfs()
      }
      rebuildNow()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      log.error("Can't rebuild the pyproject.toml model", e)
    }
  }

  /**
   * Reads every `pyproject.toml` of the project and applies the result to the workspace model.
   *
   * Public for a test and for a caller that must rebuild at once, such as a project generator.
   */
  @VisibleForTesting
  @ApiStatus.Internal
  suspend fun rebuildNow() {
    saveTomlDocuments()

    val projectRoots = withContext(Dispatchers.IO) { getRootPaths() }
    knownRoots = projectRoots
    val excludedPaths = collectExcludedPaths(project)
    // The two INFO lines below are the measurement of PY-91841. Keep them: the search and the apply have very
    // different costs, and only a split number tells which one a slow project load comes from.
    val searchStart = System.nanoTime()
    val files = findPyProjectTomlWithContent(projectRoots, excludedPaths)
    val applyStart = System.nanoTime()
    log.info("Found ${files.tomlFiles.size} pyproject.toml files in ${millisSince(searchStart)} ms")
    log.debug { "Files found: ${files.tomlFiles.keys.joinToString(", ")}" }

    rebuildProjectModel(project, files)
    log.info("Model of ${files.tomlFiles.size} pyproject.toml files applied in ${millisSince(applyStart)} ms")
    // Even though we have no entities, we still "rebuilt" the model, time to configure SDK
    notifyModelRebuilt(project)
  }

  /**
   * Loads the project tree into the VFS, once, before the first build.
   *
   * The scanning pass and the initial VFS refresh reach the content roots only. A directory that no content
   * root covers is therefore unknown to the VFS, and the filename index cannot report its `pyproject.toml`.
   * That directory is the one this feature has to turn into a module, so the load must happen (PY-91841).
   *
   * Every later change arrives as a VFS event, and the listener loads the new subtree itself.
   * This method therefore runs one time for each session.
   */
  private suspend fun loadProjectRootsIntoVfs() {
    val localFileSystem = LocalFileSystem.getInstance()
    val rootDirectories = withContext(Dispatchers.IO) {
      getRootPaths().mapNotNullTo(LinkedHashSet()) { localFileSystem.refreshAndFindFileByNioFile(it) }
    }
    loadSubtreesIntoVfs(rootDirectories, collectExcludedPaths(project))
  }

  /**
   * Writes every unsaved `pyproject.toml` to disk, because the search reads the file and not the document.
   *
   * Only a `pyproject.toml` is saved. A save of every document would cost an EDT write action for each event
   * batch, and the save of a `pyproject.toml` itself emits a content change event, hence one more rebuild.
   */
  private suspend fun saveTomlDocuments() {
    val fileDocumentManager = FileDocumentManager.getInstance()
    fun Document.isPyProjectToml(): Boolean = fileDocumentManager.getFile(this)?.name == PY_PROJECT_TOML
    if (fileDocumentManager.unsavedDocuments.none { it.isPyProjectToml() }) return
    edtWriteAction {
      for (document in fileDocumentManager.unsavedDocuments) {
        if (document.isPyProjectToml()) {
          fileDocumentManager.saveDocument(document)
        }
      }
    }
  }

  /**
   * Project might have several "attached" modules outside its base path.
   */
  @RequiresBackgroundThread
  private fun getRootPaths(): Set<Path> {
    // guessPath doesn't work: it returns first module path
    val projectRootDir = project.stateStore.projectBasePath
    // Read the content root from the workspace model, not through `Module.baseDir`. `baseDir` gives a `VirtualFile`, and
    // the VFS resolves a Windows 8.3 short name while `projectBasePath` keeps it. Two forms of one directory make
    // `computeMinimalRoots` keep two roots, so the walk finds one `pyproject.toml` twice and the sync adds a second
    // module `<name>@1` (PY-91133). The rest of the sync compares against the workspace url too.
    val modulePaths = project.workspaceModel.currentSnapshot.entities<ModuleEntity>()
      .flatMap { it.contentRoots }
      .map { it.url.toPath() }
    return computeMinimalRoots(sequenceOf(projectRootDir) + modulePaths)
  }

  private class Session(val disposable: Disposable, val job: Job, val wsmTrackerJob: Job)

  private companion object {
    val log = fileLogger()
  }
}

private fun millisSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

/** A wait of this length turns a burst of VFS events into one rebuild. */
private val DEBOUNCE: Duration = 300.milliseconds

/**
 * How long the sync waits for a platform step before it builds the model anyway.
 *
 * The value is longer than the scanning pass of a large monorepo, because a build during the scan is work that
 * a later build repeats. It is not a correctness bound, see `awaitOrWarn`.
 */
private val AWAIT_TIMEOUT: Duration = 5.minutes

/**
 * A reason to rebuild the model.
 *
 * [directoriesToLoad] holds a directory whose content the VFS does not know yet: a new directory, or one
 * that stopped being excluded. The consumer loads such a subtree before it reads the filename index.
 */
internal class RebuildRequest(val directoriesToLoad: Set<VirtualFile>)

/**
 * Returns the minimal set of [paths] such that no element is a descendant of another.
 */
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
