// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.python.community.execService.impl.TargetEnvironmentRequestHandler.Companion.mapUploadRoots
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Yet another abstraction on top of Targets API. Call [mapUploadRoots].
 */
@ApiStatus.Internal
abstract class TargetEnvironmentRequestHandler<T : TargetEnvironmentRequest>(private val reqClass: Class<T>) {

  private suspend fun mapUploadRootsIfValid(
    request: TargetEnvironmentRequest,
    localDirs: Set<Path>,
    workingDirToDownload: Path?,
  ): UploadInfo? =
    if (reqClass.isInstance(request)) {
      @Suppress("UNCHECKED_CAST") // Checked one line above
      mapUploadRootsImpl(request as T, localDirs, workingDirToDownload)
    }
    else {
      null
    }

  /**
   * See [mapUploadRoots]
   */
  protected abstract suspend fun mapUploadRootsImpl(
    request: T,
    localDirs: Set<Path>,
    workingDirToDownload: Path?,
  ): UploadInfo


  internal companion object {
    private val EP_NAME: ExtensionPointName<TargetEnvironmentRequestHandler<*>> = ExtensionPointName.create(
      "Pythonid.execService.targetEnvironmentRequestHandler"
    )

    data class UploadRootWithExplicitUploadInfo(
      /**
       * Root to add to upload volumes
       */
      val root: TargetEnvironment.UploadRoot,
      /**
       * If true, call [com.intellij.python.community.execService.impl.processLaunchers.measureUploadTime]
       */
      val uploadVolumeExplicitly: Boolean,
    )

    /**
     * for [request] that needs access to  [localDirs] (with [workingDirToDownload] is a working directory) returns
     * mapping info for each [localDirs]. This info is used to fill upload volumes, and possibly upload them.
     */
    suspend fun mapUploadRoots(
      request: TargetEnvironmentRequest,
      localDirs: Set<Path>,
      workingDirToDownload: Path?,
    ): Map<Path, UploadRootWithExplicitUploadInfo> = withContext(Dispatchers.IO) {
      val uploadInfo = EP_NAME.extensionList.firstNotNullOfOrNull { it.mapUploadRootsIfValid(request, localDirs, workingDirToDownload) }
                       ?: error("No implementation of [${TargetEnvironmentRequestHandler::class.java}] is found for $request, broken bundle? " +
                                "If you are in tests, set `@TestApplicationWithEel(useLegacyTargets=true)`")
      val localToRemoteHelpersRoots =
        uploadInfo.helpersAware.preparePyCharmHelpers().helpers.associate { it.localPath to it.targetPathFun.value }

      fun localPathToTarget(localPath: Path): LocalPathToTargetResult {
        val mightBeHelperRemoteRoot = localToRemoteHelpersRoots.entries.firstOrNull { localPath.startsWith(it.key) }?.value
        return if (mightBeHelperRemoteRoot != null) {
          // This is helper. They should never be uploaded explicitly, as handlers (inheritors) take care of them.
          LocalPathToTargetResult(
            targetPath = TargetEnvironment.TargetPath.Persistent(mightBeHelperRemoteRoot),
            removeAtShutdown = false,
            uploadVolumeExplicitly = false
          )
        }
        else {
          // Just a random temp path, but we try to preserve location of workDir (most probably projDir) is set.
          LocalPathToTargetResult(
            targetPath = TargetEnvironment.TargetPath.Temporary(hint = workingDirToDownload?.pathString),
            removeAtShutdown = true,
            uploadVolumeExplicitly = true
          )
        }
      }
      localDirs.filterNot { localPath ->
        request.uploadVolumes.any { it.localRootPath == localPath }
      }.associateWith { localPath ->
        val localPathToTargetRes = localPathToTarget(localPath)
        val root = TargetEnvironment.UploadRoot(
          localRootPath = localPath,
          targetRootPath = localPathToTargetRes.targetPath,
          removeAtShutdown = localPathToTargetRes.removeAtShutdown
        )
        UploadRootWithExplicitUploadInfo(root, uploadVolumeExplicitly = localPathToTargetRes.uploadVolumeExplicitly)
      }
    }
  }

  protected data class UploadInfo(
    /**
     * Each inheritor provides it so we can access the helpers.
     */
    val helpersAware: HelpersAwareTargetEnvironmentRequest,
  )


}

private data class LocalPathToTargetResult(
  val targetPath: TargetEnvironment.TargetPath,
  val removeAtShutdown: Boolean,
  val uploadVolumeExplicitly: Boolean,
)