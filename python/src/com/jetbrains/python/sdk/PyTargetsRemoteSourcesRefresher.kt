// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.remote.RemoteSdkProperties
import com.intellij.util.PathMappingSettings
import com.intellij.util.PathUtil
import com.intellij.util.io.ZipUtil
import com.intellij.util.io.deleteWithParentsIfEmpty
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.execute
import com.jetbrains.python.run.prepareHelperScriptExecution
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.deleteExisting
import kotlin.io.path.div

private const val STATE_FILE = ".state.json"

class PyTargetsRemoteSourcesRefresher(val sdk: Sdk, project: Project) {
  private val pyRequest: HelpersAwareTargetEnvironmentRequest =
    checkNotNull(PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project))

  private val targetEnvRequest: TargetEnvironmentRequest
    get() = pyRequest.targetEnvironmentRequest

  init {
    assert(sdk !is Disposable || !Disposer.isDisposed(sdk))
  }

  @Throws(ExecutionException::class)
  fun run(indicator: ProgressIndicator) {
    val localRemoteSourcesRoot = Files.createDirectories(Paths.get(PythonSdkUtil.getRemoteSourcesLocalPath(sdk.homePath)))

    val localUploadDir = Files.createTempDirectory("remote_sync")
    val uploadVolume = TargetEnvironment.UploadRoot(localRootPath = localUploadDir, targetRootPath = TargetPath.Temporary())
    targetEnvRequest.uploadVolumes += uploadVolume

    val downloadVolume = TargetEnvironment.DownloadRoot(localRootPath = localRemoteSourcesRoot, targetRootPath = TargetPath.Temporary())
    targetEnvRequest.downloadVolumes += downloadVolume

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

    val targetIndicator = TargetProgressIndicatorAdapter(indicator)
    val environment = targetEnvRequest.prepareEnvironment(targetIndicator)

    // XXX Make it automatic
    environment.uploadVolumes.values.forEach { it.upload(".", targetIndicator) }

    val cmd = execution.buildTargetedCommandLine(environment, sdk, emptyList())
    cmd.execute(environment, indicator)

    // XXX Make it automatic
    environment.downloadVolumes.values.forEach { it.download(".", indicator) }

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
    (sdk.sdkAdditionalData as? RemoteSdkProperties)?.setPathMappings(pathMappings)
  }

  private class StateFile {
    var roots: List<RootInfo> = emptyList()
  }

  private class RootInfo {
    var path: String = ""
    @SerializedName("zip_name")
    var zipName: String = ""
    @SerializedName("invalid_entries")
    var invalidEntries: List<String> = emptyList()
  }

  companion object {
    val LOG = logger<PyTargetsRemoteSourcesRefresher>()
  }
}

