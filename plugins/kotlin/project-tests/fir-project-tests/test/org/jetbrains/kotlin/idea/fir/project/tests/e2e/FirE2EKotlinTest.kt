// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.project.tests.e2e

import org.jetbrains.kotlin.idea.base.project.test.ProjectBasedTestPreferences
import org.jetbrains.kotlin.idea.base.project.test.projects.KotlinProject
import org.jetbrains.kotlin.idea.fir.project.tests.AbstractFirProjectBasedTests

class FirE2EKotlinTest : AbstractFirProjectBasedTests() {
    override val isBenchmark: Boolean = false

    fun testKotlin() {
        val profile = ProjectBasedTestPreferences(
            warmUpIterations = 0,
            iterations = 1,
            checkForValidity = true,
        )

        test(KotlinProject.project, KotlinProject.actions, profile)
    }
}