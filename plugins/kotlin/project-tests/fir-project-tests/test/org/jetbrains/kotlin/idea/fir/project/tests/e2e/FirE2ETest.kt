// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.project.tests.e2e

import org.jetbrains.kotlin.idea.base.project.test.ProjectBasedTestPreferences
import org.jetbrains.kotlin.idea.base.project.test.projects.RustProject
import org.jetbrains.kotlin.idea.fir.project.tests.AbstractFirProjectBasedTests

class FirE2ETest : AbstractFirProjectBasedTests() {
    override val testPrefix: String = "FIR e2e"
    override val warmUpOnHelloWorldProject: Boolean = false

    fun testRustPlugin() {
        val profile = ProjectBasedTestPreferences(
            warmUpIterations = 0,
            iterations = 1,
            checkForValidity = true,
        )

        test("Rust Plugin", RustProject.project, RustProject.actions, profile)
    }
}