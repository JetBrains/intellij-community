// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.python.community.helpersLocator.PythonBundledPyiStubsLocator
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.getPathsToTransfer
import com.jetbrains.python.sdk.updateTransferredRoots
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import com.intellij.platform.util.annotations.paths.OsPath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The facts of the interpreters of this project, see [PythonInterpreterFacts]: what the SDK updater measures and what
 * the user sets. It stores facts only, and no root: [PythonInterpreterRoots] derives the roots from them.
 *
 * The flow decides where the facts live, see [isInterpreterLibraryFlowEnabled]:
 * * the interpreter library flow keeps them in the record of the interpreter, see [PythonInterpreterRecords], which the
 *   project saves;
 * * the legacy flow keeps them in the SDK of the SDK table, in its own form: the version and the user paths in the SDK
 *   data, the class roots that come from `sys.path`, and the paths of `sys.path` inside the owner module as the paths to
 *   transfer to the module. Its `sys.path` is the one that these roots state, see [sysPathOfRoots]. A path of the module
 *   is no class root of the SDK (PY-93817). A write keeps the other class roots of the SDK, such as the skeletons: they
 *   are no facts, and [PythonInterpreterRoots] gives them.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PythonInterpreterDetails(private val project: Project, scope: CoroutineScope) {
  private val changeEvents = MutableSharedFlow<PythonInterpreterKey?>(extraBufferCapacity = Int.MAX_VALUE)

  init {
    scope.launch {
      // Only the source of the flow of this session, which a restart changes: the interpreter library flow ignores the SDK
      // table, which also holds other SDKs, such as a Java one.
      val source = if (isInterpreterLibraryFlowEnabled()) PythonInterpreterRecords.getInstance(project).changes.drop(1) else sdkTableChanges()
      source.collect { changeEvents.emit(null) }
    }
  }

  /**
   * An event for each change of a fact: the key of the interpreter whose facts this service wrote, or `null` for a change
   * that another writer made to the store, such as a commit of an SDK of the SDK table. A `null` event can name no
   * interpreter, so a reader checks all of them. The roots follow a write of this service before it returns, see
   * [PythonInterpreterRoots.refresh].
   */
  val changes: Flow<PythonInterpreterKey?> get() = changeEvents

  /** The facts of [interpreter], from where its flow keeps them, see [PythonInterpreterDetails]. */
  fun facts(interpreter: PythonInterpreter): PythonInterpreterFacts =
    if (isInterpreterLibraryFlowEnabled()) recordFacts(interpreter) else sdkFacts(interpreter.sdk)

  /**
   * The version of [interpreter], as the SDK updater recorded it, such as `Python 3.12.4`, or `null` before the first
   * record.
   */
  fun version(interpreter: PythonInterpreter): String? = facts(interpreter).version

  /**
   * Writes [facts] as the facts of [interpreter], where its flow keeps them, see [PythonInterpreterDetails]. A `null`
   * version is a probe that could not answer, so it leaves the version alone: a remote host that is down must not erase
   * a good version. Returns whether a fact changed, and reports the change on [changes] only then.
   */
  fun writeFacts(interpreter: PythonInterpreter, facts: PythonInterpreterFacts): Boolean {
    val written = facts.withVersionOf(facts(interpreter))
    val changed = if (isInterpreterLibraryFlowEnabled()) writeRecordFacts(interpreter, written) else writeTableSdkFacts(interpreter, written)
    if (changed) {
      // The roots follow before the writer goes on, so a reader after the write sees them.
      PythonInterpreterRoots.getInstance(project).refresh(interpreter)
      changeEvents.tryEmit(interpreter.key)
    }
    return changed
  }

  /** Writes [version] as the version of [interpreter], and keeps its other facts, see [writeFacts]. */
  fun writeVersion(interpreter: PythonInterpreter, version: String?): Boolean =
    version != null && writeFacts(interpreter, facts(interpreter).copy(version = version))

  /**
   * Writes [sysPath] as the `sys.path` of [interpreter], and keeps its other facts, see [writeFacts]. A path that is on
   * `sys.path` twice is kept once.
   */
  fun writeSysPath(interpreter: PythonInterpreter, sysPath: List<String>): Boolean =
    writeFacts(interpreter, facts(interpreter).copy(sysPath = sysPath.distinct().mapNotNull(::toPathOrNull)))

  /** Writes [paths] as the paths that the user added to [interpreter], and keeps its other facts, see [writeFacts]. */
  fun writeAddedPaths(interpreter: PythonInterpreter, paths: List<Path>): Boolean =
    writeFacts(interpreter, facts(interpreter).copy(addedPaths = paths.distinct()))

  /** Writes [paths] as the paths that the user excluded from [interpreter], and keeps its other facts, see [writeFacts]. */
  fun writeExcludedPaths(interpreter: PythonInterpreter, paths: Set<Path>): Boolean =
    writeFacts(interpreter, facts(interpreter).copy(excludedPaths = paths))

  /** The facts that the record of [interpreter] saves. No record gives no fact. */
  private fun recordFacts(interpreter: PythonInterpreter): PythonInterpreterFacts {
    val record = PythonInterpreterRecords.getInstance(project).recordOf(interpreter.key)
    return PythonInterpreterFacts(
      version = record?.version,
      sysPath = record?.sysPath.orEmpty().mapNotNull(::toPathOrNull),
      addedPaths = record?.addedPaths.orEmpty().mapNotNull(::urlToPathOrNull),
      excludedPaths = record?.excludedPaths.orEmpty().mapNotNullTo(HashSet(), ::urlToPathOrNull),
    )
  }

  /** Writes [facts] into the record of [interpreter]. Returns whether the record changed. */
  private fun writeRecordFacts(interpreter: PythonInterpreter, facts: PythonInterpreterFacts): Boolean {
    val records = PythonInterpreterRecords.getInstance(project)
    val record = records.recordOf(interpreter.key) ?: return false
    val updated = record.copyWith(facts)
    if (updated.sameAsRecord(record)) return false
    records.put(interpreter.key, updated)
    return true
  }

  /**
   * Writes [facts] into the SDK of [interpreter] in the legacy flow, see [PythonInterpreterDetails]. The paths of the owner
   * module become the paths to transfer, and the modules that use the SDK get them as source roots. A root that the owner
   * module has from the user, such as its content root, is no path to transfer: a change of the SDK must not remove it.
   * Returns whether a fact changed.
   */
  private fun writeTableSdkFacts(interpreter: PythonInterpreter, facts: PythonInterpreterFacts): Boolean {
    val sdk = interpreter.sdk
    val roots = PythonInterpreterRoots.getInstance(project)
    val [modulePaths, libraryPaths] = roots.splitFactRootPaths(interpreter, facts)
    val transferredBefore = getPathsToTransfer(sdk)
    val ownerRoots = roots.ownerModuleRoots(interpreter).mapNotNullTo(HashSet(), ::toRoot)
    val toTransfer = modulePaths.mapNotNull(::toRoot).filterTo(LinkedHashSet()) { it !in ownerRoots || it in transferredBefore }
    val sdkChanged = writeSdkFacts(sdk, facts, libraryPaths.mapNotNull(::toRoot), toTransfer)
    if (toTransfer != transferredBefore) updateTransferredRoots(project, sdk, transferredBefore, toTransfer)
    return sdkChanged
  }

  /**
   * An event for each change of the SDK table and of an SDK in it, which the legacy flow reads. A commit of an SDK reports
   * no table event, only a change of its entity. Only the legacy flow follows it.
   */
  private fun sdkTableChanges(): Flow<Unit> = merge(
    callbackFlow {
      val connection = ApplicationManager.getApplication().messageBus.connect(this)
      connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
        override fun jdkAdded(jdk: Sdk) = sendFor(jdk)

        override fun jdkRemoved(jdk: Sdk) = sendFor(jdk)

        override fun jdkNameChanged(jdk: Sdk, previousName: String) = sendFor(jdk)

        private fun sendFor(jdk: Sdk) {
          if (PythonSdkUtil.isPythonSdk(jdk, true)) trySend(Unit)
        }
      })
      awaitClose { connection.disconnect() }
    },
    project.workspaceModel.eventLog
      .filter { event -> event.getChanges(SdkEntity::class.java).any { (it.newEntity ?: it.oldEntity)?.type == PyNames.PYTHON_SDK_ID_NAME } }
      .map { },
  )

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PythonInterpreterDetails = project.service()

    /**
     * Writes [version] and [sysPath] straight into [sdk], for the platform, which sets up a legacy SDK with no project,
     * see `PythonSdkType.setupSdkPaths`. Only the legacy flow has such an SDK: the interpreter library flow keeps no SDK
     * in the SDK table, so it does nothing. A `null` [version] leaves the version alone. Returns whether the SDK changed.
     */
    @JvmStatic
    fun writeLegacySdk(sdk: Sdk, version: String?, sysPath: List<String>): Boolean {
      if (isInterpreterLibraryFlowEnabled()) return false
      val facts = sdkFacts(sdk).let { it.copy(version = version ?: it.version, sysPath = sysPath.distinct().mapNotNull(::toPathOrNull)) }
      // No project gives the owner module. The paths to transfer stay, and every other path of the facts is a class root
      // until an update in a project moves the paths of the module.
      val toTransfer = getPathsToTransfer(sdk)
      return writeSdkFacts(sdk, facts, sdkFactRoots(facts).filter { it !in toTransfer }, toTransfer)
    }

    /** The class roots that the SDK of [interpreter] holds, for a test that checks what the writes leave there. */
    @TestOnly
    fun sdkClassRootsForTests(interpreter: PythonInterpreter): Array<VirtualFile> = PythonSdkUtil.getPythonClassRoots(interpreter.sdk)
  }
}

/** The facts that the SDK of the SDK table keeps, in the legacy flow: the version, and the `sys.path` its roots state. */
private fun sdkFacts(sdk: Sdk): PythonInterpreterFacts {
  val data = sdk.sdkAdditionalData as? PythonSdkAdditionalData
  return PythonInterpreterFacts(
    version = sdk.versionString,
    sysPath = sysPathOfRoots(sdk).mapNotNull(::toPathOrNull),
    addedPaths = data.addedPaths(),
    excludedPaths = data.excludedPaths(),
  )
}

/**
 * Writes [facts] into [sdk], in its legacy form: the version, the user paths in its data, [classRoots] as the class
 * roots of `sys.path` and the added paths, and [toTransfer] as the paths to transfer. The other class roots of [sdk],
 * which [PythonInterpreterRoots] gives, stay, see [isDerivedRoot]. Returns whether the SDK changed.
 */
private fun writeSdkFacts(sdk: Sdk, facts: PythonInterpreterFacts, classRoots: List<VirtualFile>, toTransfer: Set<VirtualFile>): Boolean {
  val userPathsChanged = sdkFacts(sdk).let { it.addedPaths != facts.addedPaths || it.excludedPaths != facts.excludedPaths }
  val roots = classRoots + PythonSdkUtil.getPythonClassRoots(sdk).filter(::isDerivedRoot)
  val versionChanged = facts.version != sdk.versionString
  val rootsChanged = roots != PythonSdkUtil.getPythonClassRoots(sdk).toList()
  val transferChanged = toTransfer != getPathsToTransfer(sdk)
  if (!userPathsChanged && !versionChanged && !rootsChanged && !transferChanged) return false
  // One commit: a reader of the SDK between two commits reads a `sys.path` that misses a path, or has a removed one.
  commit(sdk) { modificator ->
    if (userPathsChanged || transferChanged) {
      val data = modificator.sdkAdditionalData as? PythonSdkAdditionalData
      if (data != null) {
        if (userPathsChanged) setUserPaths(data, facts)
        if (transferChanged) data.setPathsToTransferFromVirtualFiles(toTransfer)
        modificator.sdkAdditionalData = data
      }
    }
    if (versionChanged) modificator.versionString = facts.version
    if (rootsChanged) setClassRoots(modificator, roots)
  }
  return true
}

/** `sys.path` and the added paths of [facts], without the excluded ones, as roots. */
private fun sdkFactRoots(facts: PythonInterpreterFacts): List<VirtualFile> =
  (facts.sysPath + facts.addedPaths).filter { it !in facts.excludedPaths }.distinct().mapNotNull(::toRoot)

/**
 * Whether [root], a class root of an SDK, is one that the facts do not give, and [PythonInterpreterRoots] does: the
 * skeletons, or the bundled stubs and the typeshed of the IDE.
 */
internal fun isDerivedRoot(root: VirtualFile): Boolean {
  val path = VfsUtilCore.getVirtualFileForJar(root)?.path ?: root.path
  val skeletons = PythonSdkUtil.getSkeletonsRootPath(PathManager.getSystemPath()).let(FileUtil::toSystemIndependentName)
  val bundled = FileUtil.toSystemIndependentName(PythonBundledPyiStubsLocator.root().toString())
  return FileUtil.isAncestor(skeletons, path, false) || FileUtil.isAncestor(bundled, path, false)
}

/** Commits [version] and [roots] into [sdk], when they differ from the ones it holds. Returns whether it committed. */
internal fun commitSdk(sdk: Sdk, version: String?, roots: List<VirtualFile>): Boolean {
  val versionChanged = version != sdk.versionString
  val rootsChanged = roots != PythonSdkUtil.getPythonClassRoots(sdk).toList()
  if (!versionChanged && !rootsChanged) return false
  commit(sdk) { modificator ->
    if (versionChanged) modificator.versionString = version
    if (rootsChanged) setClassRoots(modificator, roots)
  }
  return true
}

private fun setClassRoots(modificator: SdkModificator, roots: List<VirtualFile>) {
  modificator.removeRoots(OrderRootType.CLASSES)
  roots.forEach { modificator.addRoot(it, OrderRootType.CLASSES) }
}

/** Applies [change] to [sdk] and commits it. */
private fun commit(sdk: Sdk, change: (SdkModificator) -> Unit) {
  WriteAction.runAndWait<RuntimeException> {
    val modificator = sdk.sdkModificator
    change(modificator)
    modificator.commitChanges()
  }
  // An SDK of the interpreter store is in no SDK table, so its commit reports nothing itself.
  PythonInterpreterSdks.sdkChangedInOpenProjects(sdk)
}

/** These facts, with the version of [current] when they have none: a probe that could not answer. */
private fun PythonInterpreterFacts.withVersionOf(current: PythonInterpreterFacts): PythonInterpreterFacts =
  if (version == null) copy(version = current.version) else this

/** A copy of this record with [facts]. The record keeps what is no fact. */
private fun PythonInterpreterRecords.StoredInterpreter.copyWith(facts: PythonInterpreterFacts): PythonInterpreterRecords.StoredInterpreter {
  val source = this
  return PythonInterpreterRecords.StoredInterpreter().apply {
    ownerDir = source.ownerDir
    ref = source.ref
    uuid = source.uuid
    requirementsFile = source.requirementsFile
    addedPaths += facts.addedPaths.map(::pathToUrl)
    excludedPaths += facts.excludedPaths.map(::pathToUrl).sorted()
    pathMappings += source.pathMappings
    version = facts.version
    sysPath += facts.sysPath.map { it.toString() }.distinct()
  }
}

private fun PythonInterpreterRecords.StoredInterpreter.sameAsRecord(other: PythonInterpreterRecords.StoredInterpreter): Boolean =
  version == other.version && sysPath == other.sysPath &&
  addedPaths == other.addedPaths && excludedPaths.toSet() == other.excludedPaths.toSet()

private fun PythonSdkAdditionalData?.addedPaths(): List<Path> = this?.addedPathFiles.orEmpty().mapNotNull { it.toNioPathOrNull() }

private fun PythonSdkAdditionalData?.excludedPaths(): Set<Path> = this?.excludedPathFiles.orEmpty().mapNotNullTo(HashSet()) { it.toNioPathOrNull() }

/**
 * Writes the user paths of [facts] into the SDK data of [sdk], the legacy form of these facts, see
 * [PythonSdkAdditionalData.getAddedPathFiles]. A path that names no file is dropped.
 */
internal fun commitUserPaths(sdk: Sdk, facts: PythonInterpreterFacts) {
  if (sdkFacts(sdk).let { it.addedPaths == facts.addedPaths && it.excludedPaths == facts.excludedPaths }) return
  commit(sdk) { modificator ->
    val data = modificator.sdkAdditionalData as? PythonSdkAdditionalData ?: return@commit
    setUserPaths(data, facts)
    modificator.sdkAdditionalData = data
  }
}

private fun setUserPaths(data: PythonSdkAdditionalData, facts: PythonInterpreterFacts) {
  val files = LocalFileSystem.getInstance()
  data.setAddedPathsFromVirtualFiles(facts.addedPaths.mapNotNullTo(LinkedHashSet()) { files.findFileByNioFile(it) })
  data.setExcludedPathsFromVirtualFiles(facts.excludedPaths.mapNotNullTo(LinkedHashSet()) { files.findFileByNioFile(it) })
}

private fun pathToUrl(path: Path): String = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(path.toString()))

private fun urlToPathOrNull(url: String): Path? = toPathOrNull(VfsUtilCore.urlToPath(url))

/** [path] as the file system names it, or [path] itself when no file is there. */
internal fun canonicalPath(path: Path): Path = LocalFileSystem.getInstance().findFileByNioFile(path)?.toNioPathOrNull() ?: path

/** The root at [path]: the directory itself, or the archive of a `.zip` or `.egg` file. `null` when no file is there. */
internal fun toRoot(path: Path): VirtualFile? {
  val file = LocalFileSystem.getInstance().findFileByNioFile(path) ?: return null
  if (file.isDirectory) return file
  val extension = FileUtilRt.getExtension(file.name).lowercase()
  return if (extension == "zip" || extension == "egg") JarFileSystem.getInstance().getJarRootForLocalFile(file) ?: file else file
}

internal fun toPathOrNull(path: @OsPath String): Path? = try {
  Path.of(path)
}
catch (_: InvalidPathException) {
  null
}
