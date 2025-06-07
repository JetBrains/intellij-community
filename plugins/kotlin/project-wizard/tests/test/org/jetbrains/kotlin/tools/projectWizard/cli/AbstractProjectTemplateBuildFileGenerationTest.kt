// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.cli

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import org.jetbrains.kotlin.tools.projectWizard.wizard.Wizard
import java.nio.file.Path

abstract class AbstractProjectTemplateBuildFileGenerationTest : AbstractBuildFileGenerationTest() {

    override fun setUp() {
        super.setUp()
        //enable experimental MPP features to enable K/JS specific wizards
        (AdvancedSettings.getInstance() as AdvancedSettingsImpl).setSetting("kotlin.mpp.experimental", true, testRootDisposable)
    }

    override fun createWizard(directory: Path, buildSystem: BuildSystem, projectDirectory: Path): Wizard =
        ProjectTemplateBasedTestWizard.createByDirectory(directory, buildSystem, projectDirectory, CLI_WIZARD_TEST_SERVICES_MANAGER)
}

