// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.idea.base.util.sdk
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.configuration.notifications.showNewKotlinCompilerAvailableNotificationIfNeeded
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingFUSCollector

class KotlinMavenImportListener(private val project: Project) : MavenImportListener {

    override fun importStarted() {
        // If the SDK is null, then the module was not loaded yet
        val allModulesLoaded = project.modules.all { it.sdk != null }
        KotlinJ2KOnboardingFUSCollector.logProjectSyncStarted(project, allModulesLoaded)
    }

    override fun importFinished(importedProjects: Collection<MavenProject>, newModules: List<Module>) {
        runInEdt {
            runWriteAction {
                if (!project.isDisposed) {
                    KotlinCommonCompilerArgumentsHolder.getInstance(project).updateLanguageAndApi(project)
                }
            }
            if (!project.isDisposed) {
                showNewKotlinCompilerAvailableNotificationIfNeeded(project)
            }
        }
        KotlinJ2KOnboardingFUSCollector.logProjectSyncCompleted(project)
    }
}
