// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.common

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.perf.Stats
import org.jetbrains.kotlin.idea.perf.WarmUpProject
import org.jetbrains.kotlin.idea.perf.live.AbstractPerformanceProjectsTest
import org.jetbrains.kotlin.idea.perf.live.PerformanceTestProfile
import org.jetbrains.kotlin.idea.perf.settingsOverride
import org.jetbrains.kotlin.idea.perf.util.TeamCity
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction

abstract class AbstractProjectBasedTest : AbstractPerformanceProjectsTest() {
    abstract val testPrefix: String

    abstract val warmUpOnHelloWorldProject: Boolean

    override fun setUp() {
        super.setUp()
        if (warmUpOnHelloWorldProject) {
            val hwStats = Stats("$testPrefix warmup project")
            val warmUpProject = WarmUpProject(hwStats)
            warmUpProject.warmUp(this)
        }
    }

    protected abstract fun invalidateCaches(project: Project)

    protected fun test(
        name: String,
        project: ProjectData,
        actions: List<ProjectAction>,
        profile: PerformanceTestProfile,
    ) {
        TeamCity.suite("$testPrefix $name") {
            Stats("$testPrefix $name").use { stats ->
                perfOpenRustPluginProject(project, stats)
                for (action in actions) {
                    runAction(action, stats, profile)
                }
            }
        }
    }


    private fun runAction(action: ProjectAction, stats: Stats, profile: PerformanceTestProfile) {
        val iterationsOverride = settingsOverride {
            warmUpIterations(profile.warmUpIterations)
            iterations(profile.iterations)
        }
        when (action) {
            is HighlightFile -> {
                perfHighlightFileEmptyProfile(
                    action.filePath,
                    stats = stats,
                    stopAtException = true,
                    tearDown = { invalidateCaches(project()) },
                    overrides = listOf(iterationsOverride),
                )
            }
            is TypeAndAutocompleteInFile -> {
                perfTypeAndAutocomplete(
                    stats,
                    fileName = action.filePath,
                    marker = action.typeAfter,
                    insertString = action.textToType,
                    highlightFileBeforeStartTyping = true,
                    lookupElements = action.expectedLookupElements,
                    note = action.note ?: "",
                    stopAtException = true,
                    overrides = listOf(iterationsOverride)
                )
            }
        }
    }


    private fun perfOpenRustPluginProject(projectData: ProjectData, stats: Stats) {
        if (projectData.openAction == ProjectOpenAction.GRADLE_PROJECT) {
            System.setProperty("org.gradle.native", "false")
        }
        myProject = perfOpenProject(
            name = projectData.name,
            stats = stats,
            note = "",
            path = projectData.path,
            openAction = projectData.openAction,
            fast = true
        )
    }
}

