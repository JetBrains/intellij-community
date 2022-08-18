/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.project.tests.fe10.perf

import org.jetbrains.kotlin.idea.project.test.base.AbstractProjectBasedTest
import org.jetbrains.kotlin.idea.project.test.base.ActionOnError
import org.jetbrains.kotlin.idea.project.test.base.ProjectBasedTestPreferences
import org.jetbrains.kotlin.idea.project.test.base.projects.KotlinProject
import org.jetbrains.kotlin.idea.project.test.base.projects.RustProject
import org.jetbrains.kotlin.idea.project.tests.fe10.FE10FrontedConfiguration

class Fe10KotlinPerformanceTest : AbstractProjectBasedTest() {
    fun testKotlinPlugin() {
        val profile = ProjectBasedTestPreferences(
            warmUpIterations = 5,
            iterations = 10,
            checkForValidity = true,
            frontendConfiguration = FE10FrontedConfiguration,
            uploadResultsToEs = true,
            actionOnError = ActionOnError.DO_NOTHING,
        )

        test(KotlinProject.project, KotlinProject.actions, profile)
    }
}