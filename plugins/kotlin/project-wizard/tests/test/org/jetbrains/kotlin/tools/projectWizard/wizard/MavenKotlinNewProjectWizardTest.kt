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
internal class MavenKotlinNewProjectWizardTest : MavenKotlinNewProjectWizardTestCase() {
    override val testDirectory: String
        get() = "testData/mavenNewProjectWizard"
    override val testRoot: File? = TestMetadataUtil.getTestRoot(MavenKotlinNewProjectWizardTest::class.java)

    override fun setUp() {
        super.setUp()
        overrideKotlinPluginVersion("2.3.10")
    }

    @Test
    fun testSimpleProjectKotlin_2_3_10() = runNewProjectTestCase()
}
