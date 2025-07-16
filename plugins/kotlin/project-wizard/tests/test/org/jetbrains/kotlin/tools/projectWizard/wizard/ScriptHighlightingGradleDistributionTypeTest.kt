// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.enableInspectionTool
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ReplaceUntilWithRangeUntilInspection
import org.jetbrains.kotlin.tools.projectWizard.cli.BuildSystem
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ScriptHighlightingGradleDistributionTypeTest : AbstractProjectTemplateNewWizardProjectImportTestBase() {

    @Test
    fun testScriptHighlightingGradleWrapped() {
        doTest(DistributionType.WRAPPED)
    }

    @Test
    fun testScriptHighlightingGradleDefaultWrapped() {
        doTest(DistributionType.DEFAULT_WRAPPED)
    }

    @Test
    fun testScriptHighlightingGradleBundled() {
        doTest(DistributionType.BUNDLED)
    }

    private fun doTest(distributionType: DistributionType) {
        // Enable inspection to avoid "Can't find tools" exception (only reproducible on TeamCity)
        val wrapper = LocalInspectionToolWrapper(ReplaceUntilWithRangeUntilInspection());
        enableInspectionTool(project, wrapper, testRootDisposable);

        val directory = Paths.get("consoleApplication")
        val tempDirectory = Files.createTempDirectory(null)

        prepareGradleBuildSystem(tempDirectory, distributionType)

        runWizard(directory, BuildSystem.GRADLE_KOTLIN_DSL, tempDirectory)

        // we need code inside invokeLater in org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsUpdater.notifyRootsChanged
        // to be executed
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        checkScriptConfigurationsIfAny()
    }
}