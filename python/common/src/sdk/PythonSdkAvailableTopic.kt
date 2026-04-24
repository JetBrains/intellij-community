package com.intellij.python.common.sdk

import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.sendToClient
import org.jetbrains.annotations.ApiStatus.Internal
import kotlinx.serialization.builtins.serializer

internal val PYTHON_SDK_AVAILABLE_TOPIC: ProjectRemoteTopic<Boolean> =
  ProjectRemoteTopic("PythonSdkAvailableTopic", Boolean.serializer())

@Internal
fun sendPythonAvailableEvent(project: Project, available: Boolean) {
  PYTHON_SDK_AVAILABLE_TOPIC.sendToClient(project, available)
}
