// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.execution.ExecutionException
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironment.TargetPath
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicatorAdapter
import com.intellij.execution.target.value.getRelativeTargetPath
import com.intellij.execution.target.value.getTargetDownloadPath
import com.intellij.execution.target.value.getTargetUploadPath
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.remote.RemoteSdkProperties
import com.intellij.util.PathMappingSettings
import com.intellij.util.PathUtil
import com.intellij.util.io.ZipUtil
import com.intellij.util.io.deleteWithParentsIfEmpty
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.*
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData.Companion.pathsAddedByUser
import com.jetbrains.python.target.PyTargetAwareAdditionalData.Companion.pathsRemovedByUser
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.setPosixFilePermissions


private const val STATE_FILE = ".state.json"

@ApiStatus.Internal

class PyTargetsRemoteSourcesRefresher(val sdk: Sdk, private val project: Project) {
  private val pyRequest: HelpersAwareTargetEnvironmentRequest =
    checkNotNull(PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project))

  private val targetEnvRequest: TargetEnvironmentRequest
    get() = pyRequest.targetEnvironmentRequest

  init {
    assert(sdk !is Disposable || !Disposer.isDisposed(sdk))
  }

  @Throws(ExecutionException::class)
  fun run(indicator: ProgressIndicator) {
    val localRemoteSourcesRoot = Files.createDirectories(sdk.remoteSourcesLocalPath)

    val localUploadDir = Files.createTempDirectory("remote_sync")
    if (Files.getFileStore(localUploadDir).supportsFileAttributeView("posix")) {
      // The directory needs to be readable to all users in case the helpers are run as another user
      localUploadDir.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    val uploadVolume = TargetEnvironment.UploadRoot(localRootPath = localUploadDir, targetRootPath = TargetPath.Temporary())
    targetEnvRequest.uploadVolumes += uploadVolume

    val downloadVolume = TargetEnvironment.DownloadRoot(localRootPath = localRemoteSourcesRoot, targetRootPath = TargetPath.Temporary())
    targetEnvRequest.downloadVolumes += downloadVolume

    pyRequest.targetEnvironmentRequest.ensureProjectSdkAndModuleDirsAreOnTarget(project)
    val execution = prepareHelperScriptExecution(helperPackage = PythonHelper.REMOTE_SYNC, helpersAwareTargetRequest = pyRequest)

    val stateFilePath = localRemoteSourcesRoot / STATE_FILE
    val stateFilePrevTimestamp: FileTime
    if (Files.exists(stateFilePath)) {
      stateFilePrevTimestamp = Files.getLastModifiedTime(stateFilePath)
      Files.copy(stateFilePath, localUploadDir / STATE_FILE)
      execution.addParameter("--state-file")
      execution.addParameter(uploadVolume.getTargetUploadPath().getRelativeTargetPath(STATE_FILE))
    }
    else {
      stateFilePrevTimestamp = FileTime.from(Instant.MIN)
    }
    execution.addParameter(downloadVolume.getTargetDownloadPath())

    val targetWithVfs = sdk.targetEnvConfiguration?.let { PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(it) }
    if (targetWithVfs != null) {
      // If sdk is target that supports local VFS, there is no reason to copy editable packages to remote_sources
      // since their paths should be available locally (to be edited)
      // Such packages are in user content roots, so we report them to remote_sync script
      val moduleRoots = project.modules.flatMap { it.rootManager.contentRoots.asList() }.mapNotNull {
        targetWithVfs.getTargetPathFromVfs(it)
      }
      if (moduleRoots.isNotEmpty()) {
        execution.addParameter("--project-roots")
        for (root in moduleRoots) {
          execution.addParameter(root)
        }
      }
    }


    val targetIndicator = TargetProgressIndicatorAdapter(indicator)
    val environment = targetEnvRequest.prepareEnvironment(targetIndicator)
    try {
      // XXX Make it automatic
      environment.uploadVolumes.values.forEach { it.upload(".", targetIndicator) }

      val cmd = execution.buildTargetedCommandLine(environment, sdk, emptyList())
      cmd.execute(environment, indicator)

      // XXX Make it automatic
      environment.downloadVolumes.values.forEach { it.download(".", indicator) }
    }
    finally {
      environment.shutdown()
    }
    if (!Files.exists(stateFilePath)) {
      throw IllegalStateException("$stateFilePath is missing")
    }
    if (Files.getLastModifiedTime(stateFilePath) <= stateFilePrevTimestamp) {
      throw IllegalStateException("$stateFilePath has not been updated")
    }

    val stateFile: StateFile
    Files.newBufferedReader(stateFilePath).use {
      stateFile = Gson().fromJson(it, StateFile::class.java)
    }

    val pathMappings = PathMappingSettings()

    // Preserve mappings for paths added by user and explicitly excluded by user
    // We may lose these mappings otherwise
    (sdk.sdkAdditionalData as? PyTargetAwareAdditionalData)?.let { pyData ->
      (pyData.pathsAddedByUser + pyData.pathsRemovedByUser).forEach { (localPath, remotePath) ->
        pathMappings.add(PathMappingSettings.PathMapping(localPath.toString(), remotePath))
      }
    }
    for (root in stateFile.roots) {
      val remoteRootPath = root.path
      val localRootName = remoteRootPath.hashCode().toString()
      val localRoot = Files.createDirectories(localRemoteSourcesRoot / localRootName)
      pathMappings.addMappingCheckUnique(localRoot.toString(), remoteRootPath)

      val rootZip = localRemoteSourcesRoot / root.zipName
      ZipUtil.extract(rootZip, localRoot, null, true)
      for (invalidEntryRelPath in root.invalidEntries) {
        val localInvalidEntry = localRoot / PathUtil.toSystemDependentName(invalidEntryRelPath)
        LOG.debug("Removing the mapped file $invalidEntryRelPath from $remoteRootPath")
        localInvalidEntry.deleteWithParentsIfEmpty(localRemoteSourcesRoot)
      }
      rootZip.deleteExisting()
    }

    if (targetWithVfs != null) {
      // If target has local VFS, we map locally available roots to VFS instead of copying them to remote_sources
      // See how ``updateSdkPaths`` is used
      for (remoteRoot in stateFile.skippedRoots) {
        val localPath = targetWithVfs.getVfsFromTargetPath(remoteRoot)?.path ?: continue
        pathMappings.add(PathMappingSettings.PathMapping(localPath, remoteRoot))
      }
    }
    sdk.sdkModificator.apply {
      (sdkAdditionalData as? RemoteSdkProperties)?.setPathMappings(pathMappings)
      ApplicationManager.getApplication().let {
        it.invokeAndWait {
          it.runWriteAction { commitChanges() }
        }
      }
    }

    val fs = LocalFileSystem.getInstance()
    // "remote_sources" folder may now contain new packages
    // since we copied them there not via VFS, we must refresh it, so Intellij knows about them
    pathMappings.pathMappings.mapNotNull { fs.findFileByPath(it.localRoot) }.forEach { it.refresh(false, true) }
  }

  private class StateFile {
    var roots: List<RootInfo> = emptyList()

    @SerializedName("skipped_roots")
    var skippedRoots: List<String> = emptyList()
  }

  private class RootInfo {
    var path: String = ""

    @SerializedName("zip_name")
    var zipName: String = ""

    @SerializedName("invalid_entries")
    var invalidEntries: List<String> = emptyList()
    override fun toString(): String = path
  }

  companion object {
    val LOG = logger<PyTargetsRemoteSourcesRefresher>()
  }
}

