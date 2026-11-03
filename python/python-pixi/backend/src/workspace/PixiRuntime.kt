// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.workspace

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.eel.fs.EelFileUtils
import com.intellij.platform.util.annotations.paths.OsPath
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.PyProcessListener
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pytools.backend.runtime.PyToolRuntime
import com.intellij.python.pytools.backend.runtime.executeAndHandleErrors
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.EelOrTarget
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

internal suspend fun <P : PathHolder> resolvePixiWorkspacePath(
  fileSystem: FileSystem<P>, executable: P?, root: Path, relativePath: @OsPath String,
): PyResult<P> = when (fileSystem.eelOrTarget) {
  is EelOrTarget.IsEel -> fileSystem.resolveInWorkingDir(root, relativePath)?.let { PyResult.success(it) }
                        ?: PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.environment.not.available"))
  is EelOrTarget.IsTarget -> PixiWorkspaceOperations.run(root) {
    if (executable == null) return@run PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.executable.not.selected"))
    val runtime = createPixiRuntime(fileSystem, executable, root)
    runtime.execute("info", "--json", processOutputTransformer = ZeroCodeStdoutTransformer).getOr { return@run it }
    val path = runtime.resolveTargetPath(relativePath).getOr { return@run it }
    fileSystem.parsePath(path)
  }
}

internal fun <P : PathHolder> createPixiRuntime(fileSystem: FileSystem<P>, executable: P, root: Path): PixiRuntime =
  when (val machine = fileSystem.eelOrTarget) {
    is EelOrTarget.IsEel -> PixiRuntime(PyToolRuntime(fileSystem.getBinaryToExec(executable, root), ExecOptions()))
    is EelOrTarget.IsTarget -> {
      val mirror = PixiWorkspaceMirror(root, machine.target.uuid, { machine.target.createEnvironmentRequest(null) }) { path ->
        val parsed = fileSystem.parsePath(path).getOr { return@PixiWorkspaceMirror it }
        pixiFileExists(fileSystem, parsed)
      }
      val binary = BinOnTarget({ builder ->
                                 builder.setExePath(executable.toStringOnRemoteMachine())
                                 mirror.captureMapping(builder.request)
                               }, machine.target, root)
      PixiRuntime(PyToolRuntime(binary, ExecOptions()), mirror)
    }
  }

internal class PixiRuntime(private val runtime: PyToolRuntime, private val mirror: PixiWorkspaceMirror? = null) {
  fun resolveTargetPath(relativePath: @OsPath String): PyResult<String> = checkNotNull(mirror).resolvePath(relativePath)

  suspend fun <T> execute(vararg arguments: String, processOutputTransformer: ProcessOutputTransformer<T>): PyResult<T> =
    runCommand { it.execute(*arguments, processOutputTransformer = processOutputTransformer) }

  suspend fun <T> executeAndHandleErrors(vararg arguments: String, transformer: ProcessOutputTransformer<T>): PyResult<T> =
    runCommand { it.executeAndHandleErrors(*arguments, transformer = transformer) }

  suspend fun executeWithOutput(arguments: List<String>, listener: PyProcessListener): PyResult<String> =
    runCommand { ExecService().execGetStdout(it.binary, Args(*arguments.toTypedArray()), it.execOptions, listener) }

  private suspend fun <T> runCommand(command: suspend (PyToolRuntime) -> PyResult<T>): PyResult<T> {
    val options = mirror?.uploadOptions()?.getOr { return it } ?: runtime.execOptions
    val result = command(runtime.copy(execOptions = options))
    mirror?.synchronize()?.getOr { return it }
    return result
  }
}

internal class PixiWorkspaceMirror(
  private val root: Path,
  targetId: String,
  private val createRequest: () -> TargetEnvironmentRequest,
  private val fileExists: suspend (String) -> PyResult<Boolean>,
) {
  private val key = Key(root.toAbsolutePath().normalize(), targetId)
  private var currentMapping: Mapping? = null
  private var rejectedMapping = false
  private var uploadBaseline: Map<String, List<Byte>?>? = null

  fun resolvePath(relativePath: @OsPath String): PyResult<String> {
    val mapping = currentMapping
                  ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.workspace.target.mapping.required"))
    return PyResult.success(mapping.remoteRoot + mapping.separator + relativePath.replace('/', mapping.separator))
  }

  suspend fun uploadOptions(): PyResult<ExecOptions> = withContext(Dispatchers.IO) {
    synchronize().getOr { return@withContext it }
    try {
      if (createRequest().uploadVolumes.any { root != it.localRootPath && root.startsWith(it.localRootPath) }) {
        return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.workspace.target.mapping.required"))
      }
      val inputs = root.listDirectoryEntries().map { it.name }.filter { it != ".pixi" && it != ".git" }
      uploadBaseline = snapshot()
      PyResult.success(ExecOptions(uploadBeforeExecution = UploadConfig(inputs, ensureWorkingDirectoryExists = false)))
    }
    catch (e: IOException) {
      syncError(e.localizedMessage ?: e.toString())
    }
  }

  @RequiresBackgroundThread
  fun captureMapping(request: TargetEnvironmentRequest) {
    val uploadRoot = request.uploadVolumes.filter { root.startsWith(it.localRootPath) }.maxByOrNull { it.localRootPath.nameCount }
    val mapping = when (val targetPath = uploadRoot?.targetRootPath) {
      is TargetEnvironment.TargetPath.Persistent -> {
        val separator = request.targetPlatform.platform.fileSeparator
        val suffix = uploadRoot.localRootPath.relativize(root).joinToString(separator.toString())
        Mapping(targetPath.absolutePath.trimEnd('/', '\\') + if (suffix.isEmpty()) "" else "$separator$suffix", separator,
                (uploadBaseline ?: snapshot()).toMutableMap())
      }
      is TargetEnvironment.TargetPath.Temporary, null -> null
    }
    currentMapping = mapping
    rejectedMapping = mapping == null
    uploadBaseline = null
    if (mapping != null) synchronized(pending) { pending[key] = mapping }
  }

  suspend fun synchronize(): PyResult<Unit> = withContext(Dispatchers.IO) {
    if (rejectedMapping) {
      return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.workspace.target.mapping.required"))
    }
    val mapping = synchronized(pending) { pending[key] } ?: return@withContext PyResult.success(Unit)
    var stage: Path? = null
    try {
      checkLocalChanges(mapping).getOr { return@withContext it }
      val files = MIRRORED_FILES.filter { file ->
        fileExists(mapping.remoteRoot + mapping.separator + file).getOr { return@withContext it }
      }
      if (files.none { it != "pixi.lock" }) {
        return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.workspace.missing"))
      }
      val missing = MIRRORED_FILES.filter { root.resolve(it).exists() && it !in files }
      if (missing.isNotEmpty()) {
        return@withContext syncError(missing.joinToString())
      }
      val destination = Files.createTempDirectory("pixi-workspace-mirror-")
      stage = destination
      val request = createRequest()
      val downloadRoot = TargetEnvironment.DownloadRoot(destination, TargetEnvironment.TargetPath.Persistent(mapping.remoteRoot))
      request.downloadVolumes.add(downloadRoot)
      val environment = request.prepareEnvironment(TargetProgressIndicator.EMPTY)
      try {
        val volume = checkNotNull(environment.downloadVolumes[downloadRoot])
        for (file in files) coroutineToIndicator { volume.download(file, it) }
      }
      finally {
        environment.shutdown()
      }
      checkLocalChanges(mapping).getOr { return@withContext it }
      for (file in files) {
        val downloaded = fingerprint(destination.resolve(file))
        checkLocalChanges(mapping).getOr { return@withContext it }
        Files.move(destination.resolve(file), root.resolve(file), REPLACE_EXISTING)
        mapping.base[file] = downloaded
      }
      synchronized(pending) { pending.remove(key) }
      PyResult.success(Unit)
    }
    catch (e: IOException) {
      syncError(e.localizedMessage ?: e.toString())
    }
    catch (e: ExecutionException) {
      syncError(e.localizedMessage ?: e.toString())
    }
    finally {
      stage?.let { EelFileUtils.deleteRecursively(it) }
    }
  }

  private suspend fun checkLocalChanges(mapping: Mapping): PyResult<Unit> {
    val changed = MIRRORED_FILES.filter { file ->
      val path = root.resolve(file)
      val unsaved = readAction {
        LocalFileSystem.getInstance().findFileByNioFile(path)?.let { FileDocumentManager.getInstance().isFileModified(it) } == true
      }
      unsaved || fingerprint(path) != mapping.base[file]
    }
    return if (changed.isEmpty()) PyResult.success(Unit)
    else PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.workspace.sync.conflict", changed.joinToString()))
  }

  private fun snapshot(): Map<String, List<Byte>?> = MIRRORED_FILES.associateWith { fingerprint(root.resolve(it)) }

  private data class Key(val root: Path, val targetId: String)
  private data class Mapping(val remoteRoot: String, val separator: Char, val base: MutableMap<String, List<Byte>?>)

  companion object {
    private val MIRRORED_FILES = listOf("pixi.toml", "pyproject.toml", "pixi.lock")
    private val pending = HashMap<Key, Mapping>()

    private fun fingerprint(path: Path): List<Byte>? {
      if (!Files.exists(path)) return null
      val digest = MessageDigest.getInstance("SHA-256")
      Files.newInputStream(path).use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
          val count = stream.read(buffer)
          if (count < 0) break
          digest.update(buffer, 0, count)
        }
      }
      return digest.digest().toList()
    }
  }
}

private fun <T> syncError(details: String): PyResult<T> =
  PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.workspace.sync.failed", details))
