// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.project.tests.perf

import org.jetbrains.kotlin.idea.base.project.test.ProjectBasedTestPreferences
import org.jetbrains.kotlin.idea.base.project.test.projects.RustProject
import org.jetbrains.kotlin.idea.fir.project.tests.AbstractFirProjectBasedTests

class FirRustPerformanceTest : AbstractFirProjectBasedTests() {
    override val testPrefix: String = "FIR"
    override val warmUpOnHelloWorldProject: Boolean = true

    fun testRustPlugin() {
        val profile = ProjectBasedTestPreferences(
            warmUpIterations = 5,
            iterations = 10,
            checkForValidity = true,
        )

        test("Rust Plugin", RustProject.project, RustProject.actions, profile)
    }
}