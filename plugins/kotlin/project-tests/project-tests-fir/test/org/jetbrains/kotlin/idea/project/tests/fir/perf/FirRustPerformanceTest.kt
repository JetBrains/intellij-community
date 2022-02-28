// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.tests.fir.perf

import org.jetbrains.kotlin.idea.project.test.base.AbstractProjectBasedTest
import org.jetbrains.kotlin.idea.project.test.base.ActionOnError
import org.jetbrains.kotlin.idea.project.test.base.ProjectBasedTestPreferences
import org.jetbrains.kotlin.idea.project.test.base.projects.RustProject
import org.jetbrains.kotlin.idea.project.tests.fir.FirFrontedConfiguration

class FirRustPerformanceTest : AbstractProjectBasedTest() {
    fun testRustPlugin() {
        val profile = ProjectBasedTestPreferences(
            warmUpIterations = 5,
            iterations = 10,
            checkForValidity = true,
            frontendConfiguration = FirFrontedConfiguration,
            uploadResultsToEs = true,
            actionOnError = ActionOnError.THROW,
        )

        test(RustProject.project, RustProject.actions, profile)
    }
}