// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.remote.CredentialsType
import com.intellij.util.PathMappingSettings
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.File
import java.util.function.Consumer

typealias PathMappings = List<PathMappingSettings.PathMapping>

/**
 * ProjectSynchronizer is an engine that synchronize code between local and remote system or between java (which is local)
 * and python (which may be remote).
 * This engine is sdk-specific and used by [com.jetbrains.python.newProject.DeprecatedUtils] (and friends).
 *
 * When generator creates remote project, it may use python helpers (with aid of tasks) and it may need some way
 * to pull remote files, patch them and push 'em back. The way it does it is skd-specific and this interface encapsulates it.
 *
 * Using this engine makes your generator compatible with remote interpreters.
 *
 * Project synchronizer is also responsible for project configuration for sync: it cooperates with user to make sure remote project is
 * configured correctly.
 * @author Ilya.Kazakevich
 */
interface PyProjectSynchronizer {

  /**
   * Checks if sync is available.
   * It supports several strategies: see concrete instance documentation.
   *
   * @param syncCheckStrategy strategy to check if sync is available.
   * Several strategies are supported: see concrete instance documentation.
   * @return null if sync is available or error message if something prevents project from sync.
   */

  @Nls fun checkSynchronizationAvailable(syncCheckStrategy: PySyncCheckStrategy): String?

  /**
   * if remote box allows user to configure remote path, this method returns default path
   * that should be shown to user.
   *
   * Must return null if [getAutoMappings] are not null.
   */
  fun getDefaultRemotePath(): String?

  /**
   * If remote box does not allow user to configure path mapping then these mappings could be used to automatically convert
   * local path to remote path. If set, can't be empty and can't coexist with [getDefaultRemotePath]
   */
  fun getAutoMappings(): com.jetbrains.python.Result<PathMappings, String>? = null


  /**
   * Synchronizes project.
   * @param module current module
   * @param syncDirection local-to-remote (aka java-to-python) or opposite. See enum value doc.
   * @param callback code to be called after sync completion. Argument tells if sync was success or not.
   * @param fileNames files to be used as source (local files in case of java-to-python, remote otherwise).
   *                  If no file provided, *all* files are copied. So, use this arg as filter to sync subset of files.
   */
  fun syncProject(module: Module, syncDirection: PySyncDirection,
                  callback: Consumer<Boolean>?, vararg fileNames: String)

  /**
   * Maps file name from one side to another.
   * @param filePath local file name (in case of java-to-python), remote otherwise
   */
  fun mapFilePath(project: Project, direction: PySyncDirection, filePath: String): String?
}

/**
 * Plugin registers [PyProjectSynchronizer] for [CredentialsType]
 */
@ApiStatus.Internal
interface PyProjectSynchronizerProvider {
  fun getSynchronizer(credsType: CredentialsType<*>, sdk: Sdk): PyProjectSynchronizer?

  companion object {
    val EP_NAME: ExtensionPointName<PyProjectSynchronizerProvider> = ExtensionPointName.create("Pythonid.projectSynchronizerProvider")

    fun find(credsType: CredentialsType<*>, sdk: Sdk) = EP_NAME.extensions.mapNotNull { it.getSynchronizer(credsType, sdk) }.firstOrNull()

    /**
     * Returns [PyProjectSynchronizer] that is suitable for remote Python
     * [sdk].
     *
     * Returns [PyUnknownProjectSynchronizer.INSTANCE] if [sdk] is remote but
     * no [PyProjectSynchronizer] is registered for this type of Python SDK.
     *
     * Returns `null` if [sdk] is local or it is not Python SDK.
     */
    @JvmStatic
    fun getSynchronizer(sdk: Sdk): PyProjectSynchronizer? {
      val sdkAdditionalData = sdk.sdkAdditionalData
      if (sdkAdditionalData is PyRemoteSdkAdditionalDataBase) {
        return find(sdkAdditionalData.remoteConnectionType, sdk) ?: PyUnknownProjectSynchronizer.INSTANCE
      }
      return null
    }
  }
}

/**
 * Several strategies to be used for [PyProjectSynchronizer.checkSynchronizationAvailable].
 * See concrete impls.
 */
sealed interface PySyncCheckStrategy

/**
 * Checks if specific folder could be synced with remote interpreter.
 * It does not cooperate with user but simply checks folder instead.
 *
 * Strategy should return "false" only if it is technically impossible to sync with this folder what ever user does.
 * If it is possible but requires some aid from user should return true.
 *
 * No remote project creation would be allowed if this strategy returns "false".
 */
class PySyncCheckOnly(val projectBaseDir: File) : PySyncCheckStrategy

/**
 * Checks if project with specific module could be synced with remote server.
 * It may contact user taking one through some wizard steps to configure project to support remote interpreter.
 * So, it does its best to make project synchronizable.*
 *
 * @param remotePath user provided remote path. Should only be provided if [PyProjectSynchronizer.getDefaultRemotePath] is not null.
 * This argument should only be provided first time. On next call always provide null to prevent infinite loop because
 * user will be asked for path only if this argument is null.
 */
class PySyncCheckCreateIfPossible(val module: Module, val remotePath: String?) : PySyncCheckStrategy

/**
 * Local-remote sync direction
 */
enum class PySyncDirection {
  LOCAL_TO_REMOTE,
  REMOTE_TO_LOCAL,
}