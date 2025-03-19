// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import java.io.File

class DefaultDataUpToDateTest : BasePlatformTestCase() {
    fun testDefaultDataUpToDate() {
        val applicationVersion = ApplicationInfo.getInstance().fullVersion
        val generatedFolder = File(KotlinRoot.DIR, "project-wizard/core/generated")

        WizardDefaultDataGeneratorSettings.getGenerators().forEach { generator ->
            val generatedOutput = generator.generateDefaultData(applicationVersion)
            val existingFile = File(generatedFolder, generator.ktFileName)

            assertTrue(
                "Could not find generated ${generator.ktFileName}. Please run the 'Generate Kotlin Wizard Default Data' task.",
                existingFile.isFile
            )

            // We drop the copyright notice because we do not care for the year to be up-to-date here
            val generatedContent = generatedOutput.lines().drop(1)
            val existingContent = existingFile.readLines().drop(1)

            assertEquals(
                "Generated file ${generator.ktFileName} differs from Json content and is likely outdated. Please run the 'Generate Kotlin Wizard Default Data' task.",
                generatedContent,
                existingContent
            )
        }
    }
}