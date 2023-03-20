// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.progress.util.BackgroundTaskUtil.runUnderDisposeAwareIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.configuration.ui.KotlinConfigurationCheckerService
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable

class KotlinExternalSystemSyncListener : ExternalSystemTaskNotificationListenerAdapter() {
    override fun onStart(id: ExternalSystemTaskId, workingDir: String) {
        val project = id.findResolvedProject() ?: return
        runUnderDisposeAwareIndicator(KotlinPluginDisposable.getInstance(project)) {
            KotlinConfigurationCheckerService.getInstance(project).syncStarted()
        }
    }

    override fun onEnd(id: ExternalSystemTaskId) {
        // At this point changes might be still not applied to project structure yet.
        val project = id.findResolvedProject() ?: return
        runUnderDisposeAwareIndicator(KotlinPluginDisposable.getInstance(project)) {
            KotlinConfigurationCheckerService.getInstance(project).syncDone()
        }
    }
}

internal fun ExternalSystemTaskId.findResolvedProject(): Project? {
    if (type != ExternalSystemTaskType.RESOLVE_PROJECT) return null
    return findProject()
}
