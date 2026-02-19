package com.intellij.python.sdkConfigurator.frontend.rpcBridge

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.python.sdkConfigurator.common.impl.ModulesDTO
import com.intellij.python.sdkConfigurator.common.impl.SHOW_SDK_CONFIG_UI_TOPIC
import com.intellij.python.sdkConfigurator.common.impl.SdkConfiguratorBackEndApi
import com.intellij.python.sdkConfigurator.frontend.askUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class FrontendTopicListener : ProjectRemoteTopicListener<ModulesDTO> {
  override val topic: ProjectRemoteTopic<ModulesDTO> = SHOW_SDK_CONFIG_UI_TOPIC

  override fun handleEvent(project: Project, event: ModulesDTO) {
    val scope = project.service<MyService>().scope
    scope.launch {
      // Ask user to choose modules, then ask backend to configure it
      askUser(project, event) { modulesChosenByUser ->
        scope.launch {
          SdkConfiguratorBackEndApi().configureSdkForModules(project.projectId(), modulesChosenByUser)
        }
      }
    }
  }
}


@Service(Level.PROJECT)
private class MyService(val scope: CoroutineScope)
