package com.intellij.python.sdkConfigurator.common.impl

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable

/**
 * Front calls back
 */
@Rpc
interface SdkConfiguratorBackEndApi : RemoteApi<Unit> {
  /***
   * Configure SDK for all modules in [projectId] if their names in [onlyModules] unconditionally
   */
  suspend fun configureSdkAutomatically(projectId: ProjectId, onlyModules: Set<ModuleName>)

  /**
   * Ask user about modules, then call [configureSdkAutomatically]
   */
  suspend fun configureAskingUser(projectId: ProjectId)
}

typealias ModuleName = String

/**
 * Ask user to choose from [ModulesDTO] and then call [SdkConfiguratorBackEndApi.configureSdkAutomatically]
 */
val SHOW_SDK_CONFIG_UI_TOPIC: ProjectRemoteTopic<ModulesDTO> = ProjectRemoteTopic("PySDKConfigurationUITopic", ModulesDTO.serializer())

/**
 * Module to parent (workspace) or null if module doesn't have a parent (not a part of workspace)
 */
@Serializable
data class ModulesDTO(val modules: Map<ModuleName, ModuleName?>) {
}

/**
 * [SdkConfiguratorBackEndApi] instance
 */
suspend fun SdkConfiguratorBackEndApi(): SdkConfiguratorBackEndApi = RemoteApiProviderService.resolve(remoteApiDescriptor<SdkConfiguratorBackEndApi>())