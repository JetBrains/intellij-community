// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.python.community.helpersLocator.PythonBundledPyiStubsLocator
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.intellij.util.PathMappingSettings
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jdom.Element
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.util.InvalidDataException
import com.jetbrains.python.PyNames
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The SDK of each interpreter of this project, in the interpreter library flow, see [isInterpreterLibraryFlowEnabled].
 * An SDK lives in memory only. It is in no SDK table, and nothing saves it: [PythonInterpreterRecords] saves the record of
 * each interpreter, and [PythonInterpreterRegistry] builds the SDK from the ref of the interpreter and that record.
 *
 * Code that still reads an [Sdk] edits it through its modificator. A writer then calls [sdkChanged], and the record of
 * the SDK goes back to the store, see [recordOf].
 *
 * This service owns its SDKs, as the SDK table owns its own: an SDK that leaves it is disposed, and every SDK is disposed
 * when the project closes. A watcher or a cache parented to an SDK goes with it.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PythonInterpreterSdks(private val project: Project) : Disposable {
  private val sdks = ConcurrentHashMap<PythonInterpreterKey, Sdk>()

  /** The SDK of [key], or `null`. */
  fun sdkOf(key: PythonInterpreterKey): Sdk? = sdks[key]

  /** Every SDK. */
  fun sdks(): List<Sdk> = sdks.values.toList()

  /** The key of [sdk], or `null` when this service does not hold it. */
  fun keyOf(sdk: Sdk): PythonInterpreterKey? = sdks.entries.firstOrNull { it.value === sdk }?.key

  /**
   * Holds [sdk] as the SDK of [key], and stores its record. An SDK held before for [key] is disposed. The data of [sdk]
   * must name the owner directory of [key], so that the interpreter built from it has [key]. The record keeps its facts
   * when [sdk] is the SDK held before, and takes them from [sdk] otherwise, see [recordOf].
   *
   * One SDK is one interpreter. When the data of [sdk] changed its ref, the key it had before goes with its record.
   */
  fun put(key: PythonInterpreterKey, sdk: Sdk) {
    val store = PythonInterpreterRecords.getInstance(project)
    for (oldKey in sdks.entries.filter { it.value === sdk && it.key != key }.map { it.key }) {
      sdks.remove(oldKey)
      store.remove(oldKey)
    }
    val previous = sdks.put(key, sdk)
    previous?.takeIf { it !== sdk }?.let(::dispose)
    // The facts of the record are of the SDK held before. Another SDK, such as a new SDK for the same key, brings its own.
    store.put(key, recordOf(key, sdk, store.recordOf(key).takeIf { previous === sdk }))
  }

  /** Drops the SDK and the record of [key]. The caller disposes the SDK, see [dispose], when no reader holds it. */
  fun remove(key: PythonInterpreterKey): Sdk? {
    PythonInterpreterRecords.getInstance(project).remove(key)
    return sdks.remove(key)
  }

  /** Disposes [sdk], an SDK that left this service, so what is parented to it goes too. */
  fun dispose(sdk: Sdk) {
    if (sdk is Disposable && sdks.values.none { it === sdk }) Disposer.dispose(sdk)
  }

  /**
   * Stores the data of [sdk] in its record again, after a commit of it, see [recordOf]. The facts of the record stay: the
   * SDK holds none. Does nothing for an SDK that this service does not hold.
   */
  fun sdkChanged(sdk: Sdk) {
    val key = keyOf(sdk) ?: return
    val store = PythonInterpreterRecords.getInstance(project)
    store.put(key, recordOf(key, sdk, store.recordOf(key)))
  }

  override fun dispose() {
    val all = sdks.values.toList()
    sdks.clear()
    all.forEach(::dispose)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PythonInterpreterSdks = project.service()

    /** [sdkChanged] in every open project, for a writer that has no project, such as the SDK updater. */
    @JvmStatic
    fun sdkChangedInOpenProjects(sdk: Sdk) {
      for (project in ProjectManager.getInstance().openProjects) {
        project.serviceIfCreated<PythonInterpreterSdks>()?.sdkChanged(sdk)
      }
    }

    /**
     * A copy of [sdk] that is in no SDK table, or `null` when [sdk] cannot be written. For a test that shares one SDK
     * between light projects, each of which disposes the SDK it holds.
     */
    @TestOnly
    @JvmStatic
    fun copyOf(sdk: Sdk): Sdk? {
      val element = Element("sdk")
      (sdk as? ProjectJdkImpl)?.writeExternal(element) ?: return null
      val sdkType = SdkType.findByName(PyNames.PYTHON_SDK_ID_NAME) ?: return null
      val copy = ProjectJdkTable.getInstance().createSdk(sdk.name, sdkType) as? ProjectJdkImpl ?: return null
      return try {
        copy.readExternal(element)
        copy
      }
      catch (_: InvalidDataException) {
        null
      }
    }

    /** The `sys.path` that a new record of [sdk] saves, see [recordOf]. */
    @TestOnly
    @JvmStatic
    fun recordedSysPathForTests(sdk: Sdk): List<String> = sysPathOfRoots(sdk)
  }
}

/**
 * The record of [sdk], the SDK of [key]: what its ref cannot give, see [PythonInterpreterRecords]. The facts, see
 * [PythonInterpreterFacts], come from [existing], the record that the store holds: the SDK holds no facts. A new record,
 * such as one of an SDK of the SDK table that the migration imports, takes them once from the SDK: its version, and the
 * `sys.path` and the bundled stub packages that its roots state, see [sysPathOfRoots].
 */
internal fun recordOf(
  key: PythonInterpreterKey,
  sdk: Sdk,
  existing: PythonInterpreterRecords.StoredInterpreter? = null,
): PythonInterpreterRecords.StoredInterpreter =
  PythonInterpreterRecords.StoredInterpreter().apply {
    ownerDir = key.ownerDir.toString()
    ref = key.ref.toString()
    val data = sdk.sdkAdditionalData as? PythonSdkAdditionalData
    if (existing != null) {
      version = existing.version
      sysPath += existing.sysPath
      addedPaths += existing.addedPaths
      excludedPaths += existing.excludedPaths
    }
    else {
      version = sdk.versionString
      sysPath += sysPathOfRoots(sdk)
      addedPaths += data?.addedPathFiles.orEmpty().map { it.url }
      excludedPaths += data?.excludedPathFiles.orEmpty().map { it.url }
    }
    if (data == null) return@apply
    uuid = data.uuid.toString()
    requirementsFile = data.requirementsFile
    if (data is PyTargetAwareAdditionalData) {
      pathMappings += data.pathMappings.pathMappings.map { mapping ->
        PythonInterpreterRecords.StoredPathMapping().apply {
          local = mapping.localRoot
          remote = mapping.remoteRoot
        }
      }
    }
  }

/**
 * Gives [data], the SDK data that the ref built, what [record] saved for it. The facts stay in the record, see
 * [PythonInterpreterFacts]. The SDK gets a copy of the user paths, for the code that reads them from an SDK, such as the
 * `PYTHONPATH` of a run. Nothing reads them back from it.
 */
internal fun applyRecord(data: PythonSdkAdditionalData, record: PythonInterpreterRecords.StoredInterpreter) {
  record.uuid.takeIf { it.isNotEmpty() }?.let { data.uuid = UUID.fromString(it) }
  data.requirementsFile = record.requirementsFile
  data.setAddedPathsFromUrls(record.addedPaths)
  data.setExcludedPathsFromUrls(record.excludedPaths)
  if (data is PyTargetAwareAdditionalData && record.pathMappings.isNotEmpty()) {
    data.setPathMappings(PathMappingSettings(record.pathMappings.map { PathMappingSettings.PathMapping(it.local, it.remote) }))
  }
}

/**
 * The `sys.path` that the roots of [sdk] state, for an SDK that the updater wrote in the legacy flow: the class roots
 * that come from `sys.path`, and the paths that were transferred to the module. The skeletons, the bundled stubs and the
 * added paths are not on `sys.path`, so they are left out. The order is the one of the class roots, and the transferred
 * paths go last. The next update reads the real `sys.path` again.
 */
internal fun sysPathOfRoots(sdk: Sdk): List<String> {
  val data = sdk.sdkAdditionalData as? PythonSdkAdditionalData
  val added = data?.addedPathFiles.orEmpty()
  val skeletons = PythonSdkUtil.getSkeletonsRootPath(PathManager.getSystemPath()).let(FileUtil::toSystemIndependentName)
  val bundledStubs = FileUtil.toSystemIndependentName(PythonBundledPyiStubsLocator.root().toString())
  val classRoots = sdk.rootProvider.getFiles(OrderRootType.CLASSES).filter { root ->
    val path = VfsUtilCore.getVirtualFileForJar(root)?.path ?: root.path
    root !in added && !FileUtil.isAncestor(skeletons, path, false) && !FileUtil.isAncestor(bundledStubs, path, false)
  }
  return (classRoots + data?.pathsToTransfer.orEmpty())
    .map { root -> FileUtil.toSystemDependentName(VfsUtilCore.getVirtualFileForJar(root)?.path ?: root.path) }
    .distinct()
}
