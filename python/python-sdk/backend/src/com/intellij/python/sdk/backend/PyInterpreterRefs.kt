// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.python.sdk.common.PyEnvRef
import com.jetbrains.python.venvReader.VirtualEnvReader
import com.intellij.execution.Platform
import kotlin.io.path.invariantSeparatorsPathString
import java.nio.file.Path
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.EelFileSystemFactory
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.openapi.project.Project
import com.intellij.execution.target.TargetEnvironmentsManager
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.pytools.common.FusId
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus

/** The installed tool with this id, or `null` when its plugin is not loaded. */
@ApiStatus.Internal
fun FusId.tool(): PyTool? = PyTool.findByPackageName(value)

/**
 * The ref of [sdk], or `null` when [sdk] is not a Python SDK, or has no home path or target configuration. The
 * flavor of [sdk] names its manager and its env ref, see [PythonSdkFlavor.getManagerId]. A flavor no tool manages, such
 * as a system interpreter or a plain venv, has the `pip` manager.
 */
@ApiStatus.Internal
fun interpreterRefOf(sdk: Sdk): PyInterpreterRef? {
  val data = sdk.sdkAdditionalData as? PythonSdkAdditionalData ?: return null
  val homePath = pathEnvRef(sdk) ?: return null
  val (mode, binary) = when (data) {
    is PyTargetAwareAdditionalData ->
      PyInterpreterRef.Mode.Target(data.targetEnvironmentConfiguration?.uuid ?: return null) to PathHolder.Target(homePath)
    else -> PyInterpreterRef.Mode.Native to PathHolder.Eel(homePath.toNioPathOrNull() ?: return null)
  }
  return interpreterRefOf(binary, data, mode)
}

/**
 * The ref in [mode] of the environment at [pythonBinary] with the SDK data [data]: its flavor names the manager.
 * The env ref is the one the tool gives, see [PythonSdkFlavor.toolEnvRefOf], else the binary path relative to the
 * project directory of [data], see [pathEnvRef]. For a caller that has no SDK yet, such as a create flow.
 */
@ApiStatus.Internal
fun interpreterRefOf(pythonBinary: PathHolder, data: PythonSdkAdditionalData, mode: PyInterpreterRef.Mode): PyInterpreterRef =
  PyInterpreterRef(
    mode,
    data.flavor.manager,
    data.flavorAndData.toolEnvRefOf(pythonBinary) ?: pathEnvRef(pythonBinary, projectDirOf(data)),
  )

/**
 * The base directory of the `PyProject` that [data] belongs to: its associated path, else its working directory. It
 * is a local path, also for a target SDK. `null` when [data] has neither.
 */
@ApiStatus.Internal
fun projectDirOf(data: PythonSdkAdditionalData): Path? =
  data.associatedModulePath?.takeIf { it.isNotBlank() }?.toNioPathOrNull()
  ?: data.workingDirectory.takeIf { data.hasValidWorkingDirectory() }

/** The [FusId] value of [PIP_MANAGER], for Java, which cannot use the value class. */
@ApiStatus.Internal
const val PIP_MANAGER_ID: String = "pip"

/** The manager of an environment no tool manages, such as a system interpreter or a plain venv. */
@ApiStatus.Internal
val PIP_MANAGER: FusId = FusId(PIP_MANAGER_ID)

/** The tool that manages the environments of this flavor, see [PythonSdkFlavor.getManagerId]. */
@get:ApiStatus.Internal
val PythonSdkFlavor<*>.manager: FusId
  get() = FusId(managerId)

/**
 * The env ref of an environment for a manager that names it by its path. A local path is normalized, so the
 * [Sdk.getHomePath] of an SDK and a [PathHolder] give one env ref for one file.
 *
 * A local binary inside [projectDir] gives the Python home, relative to [projectDir], such as `.venv`. The location
 * then stays the same when the project moves, and on Windows and Unix, where the binary paths differ. Any other binary
 * gives its absolute path. A path on a target is always absolute: [projectDir] is a local path, and the project
 * directory on the target is not known. [FileSystem.resolveEnvRef] reads both forms, and also a relative path with `..`.
 */
@ApiStatus.Internal
fun pathEnvRef(pythonBinary: PathHolder, projectDir: Path?): PyEnvRef = PyEnvRef(when (pythonBinary) {
  is PathHolder.Eel -> {
    val path = pythonBinary.path.normalize()
    val base = projectDir?.normalize()
    val home = path.resolvePythonHome()
    if (base != null && path.isAbsolute && home.startsWith(base) && home != base) base.relativize(home).invariantSeparatorsPathString
    else path.toString()
  }
  is PathHolder.Target -> pythonBinary.pathString
})

/**
 * The Python home that a relative [envRef] names in the project at [projectDir], whether or not it exists yet.
 * `null` for an absolute [envRef], which is a binary and not a home. See [pathEnvRef].
 */
@ApiStatus.Internal
fun resolveHomeEnvRef(projectDir: Path, envRef: PyEnvRef): Path? {
  val path = envRef.value.toNioPathOrNull()?.takeUnless { it.isAbsolute } ?: return null
  return projectDir.resolve(path).normalize()
}

/**
 * The Python home of this binary: the parent of `bin` (or `Scripts` on Windows), else the parent directory. It reads
 * only the path, so any thread can call it. A target is always Unix, as in the target [FileSystem].
 */
@ApiStatus.Internal
fun PathHolder.resolvePythonHome(): PathHolder = when (this) {
  is PathHolder.Eel -> PathHolder.Eel(path.resolvePythonHome())
  is PathHolder.Target -> PathHolder.Target(VirtualEnvReader().resolvePythonHomeFromBinaryOrDir(pathString, Platform.UNIX))
}

/** The last segment of this path, such as `.venv` for `/app/.venv`. */
@get:ApiStatus.Internal
val PathHolder.fileName: String
  get() = when (this) {
    is PathHolder.Eel -> path.fileName?.toString().orEmpty()
    is PathHolder.Target -> pathString.trimEnd('/').substringAfterLast('/')
  }

/** [pathEnvRef] for the home path of [sdk], or `null` when [sdk] has none. */
@ApiStatus.Internal
fun pathEnvRef(sdk: Sdk): String? {
  val homePath = sdk.homePath?.takeIf { it.isNotEmpty() } ?: return null
  if (sdk.sdkAdditionalData is PyTargetAwareAdditionalData) return homePath
  return homePath.toNioPathOrNull()?.normalize()?.toString() ?: homePath
}

/**
 * The file system of the machine this ref names, for [project]: the Eel machine of the project for
 * [PyInterpreterRef.Mode.Native], and the registered target for [PyInterpreterRef.Mode.Target]. `null` when no
 * target of [project] has the id of the ref, see [registerTarget].
 */
@ApiStatus.Internal
suspend fun PyInterpreterRef.fileSystem(project: Project): FileSystem<*>? = when (val mode = mode) {
  PyInterpreterRef.Mode.Native -> project.getEelDescriptor().toEelApi().toFileSystem()
  is PyInterpreterRef.Mode.Target -> findTarget(project, mode.targetId)?.let { EelFileSystemFactory.getInstance().create(it) }
}

/** The target of [project] with the id [targetId], or `null` when [project] has none. See [registerTarget]. */
@ApiStatus.Internal
fun findTarget(project: Project, targetId: String): TargetEnvironmentConfiguration? =
  TargetEnvironmentsManager.getInstance(project).targets.resolvedConfigs().firstOrNull { it.uuid == targetId }

/**
 * Registers [target] in the targets of [project], so a [PyInterpreterRef] can name it by its id. A target stores no
 * credentials there: the IDE keeps them in its credential store.
 */
@ApiStatus.Internal
fun registerTarget(project: Project, target: TargetEnvironmentConfiguration) {
  TargetEnvironmentsManager.getInstance(project).addTarget(target)
}
