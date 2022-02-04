// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.configuration.KotlinMigrationProjectService
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.util.ProgressIndicatorUtils.runUnderDisposeAwareIndicator


class MavenImportStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        val parentDisposable = KotlinPluginDisposable.getInstance(project)

        MavenProjectsManager.getInstance(project)?.addManagerListener(
            object : MavenProjectsManager.Listener {
                override fun projectsScheduled() {
                    runUnderDisposeAwareIndicator(parentDisposable) {
                        KotlinMigrationProjectService.getInstance(project).onImportAboutToStart()
                    }
                }
            },
            parentDisposable,
        )
    }
}
