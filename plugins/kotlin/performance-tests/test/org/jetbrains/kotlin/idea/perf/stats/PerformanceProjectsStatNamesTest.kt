// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.stats

import org.jetbrains.kotlin.idea.perf.live.PerformanceProjectsTest
import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig
import org.jetbrains.kotlin.idea.perf.util.ExternalProject
import org.jetbrains.kotlin.idea.perf.util.OutputConfig
import org.jetbrains.kotlin.idea.perf.suite.PerformanceSuite
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction

class PerformanceProjectsStatNamesTest: PerformanceProjectsTest() {

    override fun profileConfig(): ProfilerConfig = statsProfilerConfig

    override fun outputConfig(): OutputConfig = statsOutputConfig

    override fun PerformanceSuite.ApplicationScope.kotlinProject(block: PerformanceSuite.ProjectScope.() -> Unit) {
        project(name = "kotlin", path = ExternalProject.KOTLIN_PROJECT_PATH, openWith = ProjectOpenAction.EXISTING_IDEA_PROJECT, block = block)
    }

}