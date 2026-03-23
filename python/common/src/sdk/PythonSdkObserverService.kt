// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.common.sdk

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Service(PROJECT)
class PythonSdkObserverService {
  internal val flow = MutableStateFlow(false)

  val isPythonSdkAvailable: StateFlow<Boolean> = flow
}

internal class PythonSdkAvailableTopicListener : ProjectRemoteTopicListener<Boolean> {
  override val topic: ProjectRemoteTopic<Boolean> = PYTHON_SDK_AVAILABLE_TOPIC

  override fun handleEvent(project: Project, event: Boolean) {
    project.service<PythonSdkObserverService>().flow.value = event
  }
}
