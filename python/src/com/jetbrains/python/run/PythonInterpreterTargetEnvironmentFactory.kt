// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.run.target.HelpersAwareLocalTargetEnvironmentRequest
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.add.target.ProjectSync
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PythonInterpreterTargetEnvironmentFactory {
  fun getPythonTargetInterpreter(sdk: Sdk, project: Project): HelpersAwareTargetEnvironmentRequest?

  fun getTargetType(): TargetEnvironmentType<*>

  /**
   * Mutable targets are SSH, WSL. Immutable targets are Docker images and Docker Compose services.
   */
  fun isMutable(configuration: TargetEnvironmentConfiguration): Boolean?

  /**
   * Comply with the credentials-based Python SDKs.
   *
   * @param project is might be required for specific targets to retrieve the project-specific data
   */
  fun getDefaultSdkName(project: Project?, data: PyTargetAwareAdditionalData, version: String?): String?

  /**
   * Enables additional UI options and target-specific mechanics for project synchronization.
   */
  fun getProjectSync(project: Project?, configuration: TargetEnvironmentConfiguration): ProjectSync?

  companion object {
    const val UNKNOWN_INTERPRETER_VERSION = "unknown interpreter"

    @JvmStatic
    val EP_NAME = ExtensionPointName<PythonInterpreterTargetEnvironmentFactory>("Pythonid.interpreterTargetEnvironmentFactory")

    @JvmStatic
    fun findPythonTargetInterpreter(sdk: Sdk, project: Project): HelpersAwareTargetEnvironmentRequest? =
      when (sdk.sdkAdditionalData) {
        is PyTargetAwareAdditionalData, is PyRemoteSdkAdditionalDataBase ->
          EP_NAME.extensionList.firstNotNullOfOrNull { it.getPythonTargetInterpreter(sdk, project) }
        else -> HelpersAwareLocalTargetEnvironmentRequest()
      }

    @JvmStatic
    fun findDefaultSdkName(project: Project?, data: PyTargetAwareAdditionalData, version: String?): String =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getDefaultSdkName(project, data, version) } ?: getFallbackSdkName(data, version)

    @JvmStatic
    fun findProjectSync(project: Project?, configuration: TargetEnvironmentConfiguration): ProjectSync? =
      EP_NAME.extensionList.mapNotNull { it.getProjectSync(project, configuration) }.firstOrNull()

    /**
     * Looks for [ProjectSync] that corresponds to the provided [configuration], applies its UI to [this] [Panel] via
     * [ProjectSync.extendDialogPanelWithOptionalFields] and returns [ProjectSync].
     *
     * Does nothing if [project] is `null` or it is the default project.
     */
    @JvmStatic
    fun Panel.projectSyncRows(project: Project?, configuration: TargetEnvironmentConfiguration?): ProjectSync? =
      if (configuration != null && project != null && !project.isDefault) {
        findProjectSync(project, configuration)?.also { projectSync ->
          projectSync.extendDialogPanelWithOptionalFields(this)
        }
      }
      else null

    private fun getFallbackSdkName(data: PyTargetAwareAdditionalData, version: String?): String =
      "Remote ${version ?: UNKNOWN_INTERPRETER_VERSION} (${data.interpreterPath})"

    /**
     * Note: let the target be immutable by default though this case seems to be invalid.
     */
    @JvmStatic
    fun isMutable(configuration: TargetEnvironmentConfiguration): Boolean =
      EP_NAME.extensionList.mapNotNull { it.isMutable(configuration) }.firstOrNull() ?: false

    fun TargetEnvironmentConfiguration.isOfType(targetEnvironmentType: TargetEnvironmentType<*>): Boolean =
      typeId == targetEnvironmentType.id
  }
}