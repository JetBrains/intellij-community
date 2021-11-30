// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.stats

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.testFramework.Stats
import org.jetbrains.kotlin.idea.perf.live.PerformanceProjectsTest
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction

class PerformanceProjectsStatNamesTest: PerformanceProjectsTest() {

    override fun stats(name: String): Stats =
        Stats(name, outputConfig = statsOutputConfig, profilerConfig = statsProfilerConfig)

    override fun openProject(
        name: String,
        stats: Stats,
        note: String,
        path: String,
        openAction: ProjectOpenAction,
        fast: Boolean
    ): Project {
        try {
            return openProjectNormal(name, path, ProjectOpenAction.EXISTING_IDEA_PROJECT)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return openProjectNormal(name, path, openAction)
    }

}