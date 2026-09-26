package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.util.messages.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@Topic.ProjectLevel
@VisibleForTesting
@get:ApiStatus.Internal
val MODEL_REBUILD: Topic<ModelRebuiltListener> = Topic(ModelRebuiltListener::class.java, Topic.BroadcastDirection.NONE)


/**
 * Tell listener that model is rebuilt, so listener (SDK configurator in most cases) might configure SDK automatically
 */
internal suspend fun notifyModelRebuilt(project: Project) {
  withContext(Dispatchers.Default) {
    project.messageBus.syncPublisher(MODEL_REBUILD).modelRebuilt(project)
  }
}
