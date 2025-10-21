package com.intellij.python.sdkConfigurator.backend.impl.rpcBridge

import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.python.sdkConfigurator.backend.impl.configureSdkAskingUser
import com.intellij.python.sdkConfigurator.common.impl.DETECT_SDK_FOR_MODULES

internal class BackendTopicListener : ProjectRemoteTopicListener<Unit> {
  override val topic: ProjectRemoteTopic<Unit> = DETECT_SDK_FOR_MODULES

  override fun handleEvent(project: Project, event: Unit) {
    configureSdkAskingUser(project)
  }
}
