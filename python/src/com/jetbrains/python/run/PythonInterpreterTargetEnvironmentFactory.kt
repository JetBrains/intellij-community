// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.run.target.HelpersAwareLocalTargetEnvironmentRequest
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.add.target.ProjectSync
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.targetWithVfs.TargetWithMappedLocalVfs
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PythonInterpreterTargetEnvironmentFactory : PluginAware {
  /**
   * Docker target may also access WSL, hence returns WSL here.
   * Note, that you shouldn't return [getTargetType] here, since WSL can't run another WSL (you need to check distro),
   * but Docker can run any WSL (depending on its config)
   */
  val canProbablyRunCodeForeignTypes: List<Class<out TargetEnvironmentType<*>>> get() = emptyList()

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

  /**
   * Target provides access to its filesystem using VFS (like WSL)
   */
  fun asTargetWithMappedLocalVfs(envConfig: TargetEnvironmentConfiguration): TargetWithMappedLocalVfs? = null

  /**
   * See [isPackageManagementSupported]
   */
  fun packageManagementSupported(evConfiguration: TargetEnvironmentConfiguration): Boolean? = null

  fun isFor(configuration: TargetEnvironmentConfiguration): Boolean

  /**
   * Check sdk need to be associated with module.
   * For example to not allow sdk with DockerComposeTargetEnvironmentConfiguration to be in list of existing sdks for a new project
   */
  fun needAssociateWithModule(): Boolean = false

  /**
   * For some modules target is obvious (like ``\\wsl$\``)
   */
  fun getTargetModuleResidesOnImpl(module: Module): TargetConfigurationWithLocalFsAccess? = null

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    if (!service<Available>().isAvailable(this, pluginDescriptor)) {
      throw ExtensionNotApplicableException.create()
    }
  }

  /**
   * Overriding this service allows to disable some Run-Target based Python interpreters in different IDEs without touching code of
   * every [PythonInterpreterTargetEnvironmentFactory].
   */
  interface Available {
    fun isAvailable(factory: PythonInterpreterTargetEnvironmentFactory, pluginDescriptor: PluginDescriptor): Boolean

    /** It is supposed that PyCharm Pro supports all available Run Target interpreters. */
    class Default : Available {
      override fun isAvailable(factory: PythonInterpreterTargetEnvironmentFactory, pluginDescriptor: PluginDescriptor): Boolean = true
    }
  }

  companion object {
    const val UNKNOWN_INTERPRETER_VERSION = "unknown interpreter"

    @JvmStatic
    val EP_NAME = ExtensionPointName<PythonInterpreterTargetEnvironmentFactory>("Pythonid.interpreterTargetEnvironmentFactory")

    @JvmStatic
    fun findPythonTargetInterpreter(sdk: Sdk, project: Project): HelpersAwareTargetEnvironmentRequest =
      when (sdk.sdkAdditionalData) {
        is PyTargetAwareAdditionalData, is PyRemoteSdkAdditionalDataBase ->
          EP_NAME.extensionList.firstNotNullOfOrNull { it.getPythonTargetInterpreter(sdk, project) }
        else -> null
      } ?: HelpersAwareLocalTargetEnvironmentRequest()

    @JvmStatic
    fun findDefaultSdkName(project: Project?, data: PyTargetAwareAdditionalData, version: String?): String =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getDefaultSdkName(project, data, version) } ?: getFallbackSdkName(data, version)


    @JvmStatic
    fun findProjectSync(project: Project?, configuration: TargetEnvironmentConfiguration): ProjectSync? =
      EP_NAME.extensionList.mapNotNull { it.getProjectSync(project, configuration) }.firstOrNull()

    fun by(configuration: TargetEnvironmentConfiguration): PythonInterpreterTargetEnvironmentFactory? =
      EP_NAME.extensionList.find { it.isFor(configuration) }

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
          projectSync.extendDialogPanelWithOptionalFields(this, configuration)
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

    /**
     * Target provides access to its filesystem using VFS (like WSL)
     */
    @JvmStatic
    fun getTargetWithMappedLocalVfs(targetEnvironmentConfiguration: TargetEnvironmentConfiguration) = EP_NAME.extensionList.asSequence().mapNotNull {
      it.asTargetWithMappedLocalVfs(targetEnvironmentConfiguration)
    }.firstOrNull()

    /**
     * Null means this sdk is not target based. In other case value means if user can install package in this SDK
     */
    @JvmStatic
    fun isPackageManagementSupported(sdk: Sdk): Boolean? = (sdk.sdkAdditionalData as? PyTargetAwareAdditionalData)
      ?.targetEnvironmentConfiguration
      ?.let { targetEnvironmentConfiguration ->
        EP_NAME.extensionList.firstNotNullOfOrNull { it.packageManagementSupported(targetEnvironmentConfiguration) }
      }

    /**
     * Module may be not local but resided on target (like wsl)
     */
    @JvmStatic
    fun getTargetModuleResidesOn(module: Module): TargetConfigurationWithLocalFsAccess? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getTargetModuleResidesOnImpl(module) }
  }
}