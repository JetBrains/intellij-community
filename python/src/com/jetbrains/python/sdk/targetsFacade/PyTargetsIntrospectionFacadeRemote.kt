// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.targetsFacade

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.execution.ExecutionException
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetProgressIndicatorAdapter
import com.intellij.execution.target.value.getRelativeTargetPath
import com.intellij.execution.target.value.getTargetDownloadPath
import com.intellij.execution.target.value.getTargetUploadPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.python.community.execService.impl.processLaunchers.uploadMeasureTime
import com.intellij.util.PathMappingSettings
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.ZipUtil
import com.intellij.util.io.deleteWithParentsIfEmpty
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.ensureProjectSdkAndModuleDirsAreOnTarget
import com.jetbrains.python.run.execute
import com.jetbrains.python.run.prepareHelperScriptExecution
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.remoteSourcesLocalPath
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData.Companion.pathsAddedByUser
import com.jetbrains.python.target.PyTargetAwareAdditionalData.Companion.pathsRemovedByUser
import com.jetbrains.python.target.targetWithVfs.TargetWithMappedLocalVfs
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.setPosixFilePermissions

// Remote target
internal class PyTargetsIntrospectionFacadeRemote private constructor(
  sdk: Sdk,
  private var data: PyTargetAwareAdditionalData,
  project: Project,
  request: HelpersAwareTargetEnvironmentRequest,
  private val configuration: TargetEnvironmentConfiguration,
) : PyTargetsIntrospectionFacade(sdk, project, request) {
  companion object {

    private const val STATE_FILE = ".state.json"
    private const val SUCCESS_FILE = ".success"
    private val logger = fileLogger()

    /**
     * @return `null` if the plugin for the target is not loaded
     */
    fun create(sdk: Sdk, data: PyTargetAwareAdditionalData, project: Project): PyTargetsIntrospectionFacadeRemote? {
      val request = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(data, project) ?: return null
      val configuration = data.targetEnvironmentConfiguration ?: return null
      return PyTargetsIntrospectionFacadeRemote(sdk, data, project, request, configuration)
    }
  }

  @Throws(ExecutionException::class)
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  private fun refreshSources(indicator: ProgressIndicator) {
    val localRemoteSourcesRoot = Files.createDirectories(sdk.remoteSourcesLocalPath)

    val localUploadDir = Files.createTempDirectory("remote_sync")
    if (Files.getFileStore(localUploadDir).supportsFileAttributeView("posix")) {
      // The directory needs to be readable to all users in case the helpers are run as another user
      localUploadDir.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    val uploadVolume =
      TargetEnvironment.UploadRoot(localRootPath = localUploadDir, targetRootPath = TargetEnvironment.TargetPath.Temporary())
    targetEnvRequest.uploadVolumes += uploadVolume

    val downloadVolume =
      TargetEnvironment.DownloadRoot(localRootPath = localRemoteSourcesRoot, targetRootPath = TargetEnvironment.TargetPath.Temporary())
    targetEnvRequest.downloadVolumes += downloadVolume

    pyRequest.targetEnvironmentRequest.ensureProjectSdkAndModuleDirsAreOnTarget(project)
    val execution = prepareHelperScriptExecution(helperPackage = PythonHelper.REMOTE_SYNC, helpersAwareTargetRequest = pyRequest)

    val stateFilePath = localRemoteSourcesRoot / STATE_FILE
    if (Files.exists(stateFilePath)) {
      Files.copy(stateFilePath, localUploadDir / STATE_FILE)
      execution.addParameter("--state-file")
      execution.addParameter(uploadVolume.getTargetUploadPath().getRelativeTargetPath(STATE_FILE))
    }
    execution.addParameter(downloadVolume.getTargetDownloadPath())

    val targetWithVfs = PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(configuration)
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
      environment.uploadVolumes.values.forEach { it.uploadMeasureTime(".", targetIndicator, "sourceRefresher") }

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
    val successFilePath = localRemoteSourcesRoot / SUCCESS_FILE
    if (!Files.exists(successFilePath)) {
      throw IllegalStateException("$successFilePath is missing")
    }
    else {
      Files.delete(successFilePath)
    }

    val stateFile: StateFile
    Files.newBufferedReader(stateFilePath).use {
      stateFile = Gson().fromJson(it, StateFile::class.java)
    }

    val pathMappings = PathMappingSettings()

    // Preserve mappings for paths added by user and explicitly excluded by user
    // We may lose these mappings otherwise
    (data.pathsAddedByUser + data.pathsRemovedByUser).forEach { (localPath, remotePath) ->
      pathMappings.add(PathMappingSettings.PathMapping(localPath.toString(), remotePath))
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
        logger.debug("Removing the mapped file $invalidEntryRelPath from $remoteRootPath")
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

    commitMappings(pathMappings)

    val fs = StandardFileSystems.local()
    // "remote_sources" folder may now contain new packages
    // since we copied them there not via VFS, we must refresh it, so Intellij knows about them
    pathMappings.pathMappings.mapNotNull { fs.findFileByPath(it.localRoot) }.forEach { it.refresh(false, true) }
  }

  override val isLocalTarget: Boolean = false

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @Throws(ExecutionException::class)
  override fun synchronizeRemoteSourcesAndSetupMappingsIfNeeded(indicator: ProgressIndicator) {
    val targetWithVfs = PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(configuration)
    if (targetWithVfs != null) {
      synchronizeVfsMappedTarget(targetWithVfs, indicator)
    }
    else {
      refreshSources(indicator)
    }
  }


  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  private fun synchronizeVfsMappedTarget(
    targetWithVfs: TargetWithMappedLocalVfs,
    indicator: ProgressIndicator,
  ) {
    val remotePaths = getInterpreterPaths(indicator)
    val pathMappings = PathMappingSettings()

    // Preserve mappings for paths added/excluded by user
    (data.pathsAddedByUser + data.pathsRemovedByUser).forEach { (localPath, remotePath) ->
      pathMappings.add(PathMappingSettings.PathMapping(localPath.toString(), remotePath))
    }

    for (remotePath in remotePaths) {
      val localPath = targetWithVfs.getLocalPath(remotePath) ?: continue
      pathMappings.addMappingCheckUnique(localPath.toString(), remotePath)
    }

    commitMappings(pathMappings)

    val fs = StandardFileSystems.local()
    pathMappings.pathMappings.mapNotNull { fs.findFileByPath(it.localRoot) }.forEach { it.refresh(false, true) }
  }

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  private fun commitMappings(pathMappings: PathMappingSettings) {
    if (data.pathMappings == pathMappings) return // Do not commit the same paths twice
    sdk.sdkModificator.apply {
      // We are sure that modificator of this SDK is target, this sdk is for targets only
      (sdkAdditionalData as PyTargetAwareAdditionalData).setPathMappings(pathMappings)
      ApplicationManager.getApplication().let {
        it.invokeAndWait {
          it.runWriteAction { commitChanges() }
        }
      }
    }
    data = sdk.sdkAdditionalData as PyTargetAwareAdditionalData
  }
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