package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectLinkListener
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

// "upper-level" API to called by various actions

@Topic.ProjectLevel
internal val PROJECT_AWARE_TOPIC: Topic<ExternalSystemProjectListener> = Topic(ExternalSystemProjectListener::class.java, Topic.BroadcastDirection.NONE)

@Topic.ProjectLevel
internal val PROJECT_LINKER_AWARE_TOPIC: Topic<ExternalSystemProjectLinkListener> = Topic(ExternalSystemProjectLinkListener::class.java, Topic.BroadcastDirection.NONE)


internal val SYSTEM_ID = ProjectSystemId("PyProjectToml")


val projectModelEnabled: Boolean get() = Registry.`is`("intellij.python.pyproject.model")


internal suspend fun unlinkProjectWithProgress(project: Project, externalProjectPath: String) {
  withBackgroundProgress(project = project, title = PyProjectTomlBundle.message("intellij.python.pyproject.unlink.model")) {
    unlinkProject(project, externalProjectPath)
  }
}

internal suspend fun linkProjectWithProgress(project: Project, projectModelRoot: Path) {
  withBackgroundProgress(project = project, title = PyProjectTomlBundle.message("intellij.python.pyproject.link.and.sync.model")) {
    linkProject(project, projectModelRoot)
  }
}

internal fun linkProjectWithProgressInBackground(project: Project, projectModelRoot: Path? = null) {
  ApplicationManager.getApplication().service<MyService>().scope.launch {

    val projectModelRoot = projectModelRoot ?: withContext(Dispatchers.IO) {
      // can't guess as it might return the src of the first module
      project.baseDir.toNioPath()
    }

    linkProjectWithProgress(project, projectModelRoot)
  }
}

@Service
private class MyService(val scope: CoroutineScope)

