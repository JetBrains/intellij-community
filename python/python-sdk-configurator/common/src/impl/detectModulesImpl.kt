package com.intellij.python.sdkConfigurator.common.impl

import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import kotlinx.serialization.builtins.serializer

/**
 * Backend listens for this topic to start SDK detection process
 */
val DETECT_SDK_FOR_MODULES: ProjectRemoteTopic<Unit> = ProjectRemoteTopic("PySDKConfigurationDetectSDKTopic", Unit.serializer())

