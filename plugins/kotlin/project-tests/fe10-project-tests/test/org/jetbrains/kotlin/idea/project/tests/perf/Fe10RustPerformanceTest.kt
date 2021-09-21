// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.project.tests.perf

import org.jetbrains.kotlin.idea.AbstractFE10ProjectBasedTests
import org.jetbrains.kotlin.idea.base.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.assertKotlinPluginKind
import org.jetbrains.kotlin.idea.base.project.test.ProjectBasedTestPreferences
import org.jetbrains.kotlin.idea.base.project.test.projects.RustProject

class Fe10RustPerformanceTest : AbstractFE10ProjectBasedTests() {
    override val testPrefix: String = "FE10"
    override val warmUpOnHelloWorldProject: Boolean = true

    fun testIsCorrectPlugin() {
        assertKotlinPluginKind(KotlinPluginKind.FE10_PLUGIN)
    }

    fun testRustPlugin() {
        val profile = ProjectBasedTestPreferences(
            warmUpIterations = 5,
            iterations = 10,
            checkForValidity = false,
        )

        test("Rust Plugin", RustProject.project, RustProject.actions, profile)
    }
}