// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenSyncListener
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurationService

class KotlinConfiguratorMavenSyncListener: MavenSyncListener {
    override fun syncStarted(project: Project) {
        KotlinProjectConfigurationService.getInstance(project).onSyncStarted()
        // Removes old configuration notifications
        KotlinProjectConfigurationService.getInstance(project).refreshEditorNotifications()
    }

    override fun syncFinished(project: Project) {
        val configurationService = KotlinProjectConfigurationService.getInstance(project)
        configurationService.onSyncFinished()
    }
}