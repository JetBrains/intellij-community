package com.intellij.python.sdkConfigurator.common.impl

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor

/**
 * Front calls back
 */
@Rpc
interface SdkConfiguratorBackEndApi : RemoteApi<Unit> {
  /***
   * Configure SDK for all modules in [projectId] if their names in [onlyModules]
   */
  suspend fun configureSdkForModules(projectId: ProjectId, onlyModules: Set<ModuleName>)

}


/**
 * Ask user to choose from [ModulesDTO] and then call [SdkConfiguratorBackEndApi.configureSdkForModules]
 */
val SHOW_SDK_CONFIG_UI_TOPIC: ProjectRemoteTopic<ModulesDTO> = ProjectRemoteTopic("PySDKConfigurationUITopic", ModulesDTO.serializer())

/**
 * Module to parent (workspace) or null if module doesn't have a parent (not a part of workspace)
 */

/**
 * [SdkConfiguratorBackEndApi] instance
 */
suspend fun SdkConfiguratorBackEndApi(): SdkConfiguratorBackEndApi = RemoteApiProviderService.resolve(remoteApiDescriptor<SdkConfiguratorBackEndApi>())