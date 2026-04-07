// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.maven

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.task.ModuleBuildTask
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.configuration.hasKotlinPluginEnabled

/**
 * This listener is responsible for listening to build events and recording successful
 * events for Maven projects that have Kotlin enabled.
 */
internal class MavenBuildProcessSatisfactionListener(private val project: Project) : ProjectTaskListener {

    // We cache this for performance reasons as it is very unlikely to change.
    // If it does change, we will still get the correct results the next time the project is opened.
    private val hasKotlinPluginEnabled by lazy {
        project.modules.any { it.hasKotlinPluginEnabled() }
    }

    override fun finished(result: ProjectTaskManager.Result) {
        if (result.isAborted || result.hasErrors()) return
        if (!MavenProjectsManager.getInstance(project).isMavenizedProject) return
        // Check that at least one of the tasks is a successful build task
        if (!result.anyTaskMatches { task, state -> task is ModuleBuildTask && !state.isFailed && !state.isSkipped }) return

        if (hasKotlinPluginEnabled) {
            MavenBuildProcessSatisfactionSurveyStore.getInstance().recordKotlinBuild()
        } else {
            MavenBuildProcessSatisfactionSurveyStore.getInstance().recordNonKotlinBuild()
        }
    }
}
