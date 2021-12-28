// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.configuration.KotlinMigrationProjectService
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.util.ProgressIndicatorUtils.runUnderDisposeAwareIndicator


class MavenImportListener : StartupActivity {

    override fun runActivity(project: Project) {
        val parentDisposable = KotlinPluginDisposable.getInstance(project)
        project.messageBus.connect(parentDisposable).subscribe(
            MavenImportListener.TOPIC,
            MavenImportListener { _: Collection<MavenProject>, _: List<Module> ->
                runUnderDisposeAwareIndicator(parentDisposable) {
                    KotlinMigrationProjectService.getInstance(project).onImportFinished()
                }
            }
        )

        MavenProjectsManager.getInstance(project)?.addManagerListener(object : MavenProjectsManager.Listener {
            override fun projectsScheduled() {
                runUnderDisposeAwareIndicator(parentDisposable) {
                    KotlinMigrationProjectService.getInstance(project).onImportAboutToStart()
                }
            }
        })
    }
}