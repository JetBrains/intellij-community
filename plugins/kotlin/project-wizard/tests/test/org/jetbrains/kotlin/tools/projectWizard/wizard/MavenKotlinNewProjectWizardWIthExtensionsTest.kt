// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@TestRoot("project-wizard/tests")
@RunWith(JUnit4::class)
class MavenKotlinNewProjectWizardWIthExtensionsTest : MavenKotlinNewProjectWizardTestCase() {
    override val testDirectory: String
        get() = "testData/mavenNewProjectWizard"

    override val testRoot: File? = TestMetadataUtil.getTestRoot(MavenKotlinNewProjectWizardWIthExtensionsTest::class.java)

    override fun setUp() {
        super.setUp()
        overrideKotlinPluginVersion("2.4.0")
    }

    @Test
    fun testSimpleProjectKotlin_2_4AddsExtensionsToPlugin() = runNewProjectTestCase()
}
