// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard

import org.jetbrains.kotlin.tools.projectWizard.cli.BuildSystem
import org.jetbrains.kotlin.tools.projectWizard.cli.ProjectTemplateBasedTestWizard
import java.nio.file.Path

abstract class AbstractProjectTemplateNewWizardProjectImportTestBase : AbstractNewWizardProjectImportTest() {
    override fun createWizard(directory: Path, buildSystem: BuildSystem, projectDirectory: Path): Wizard =
        ProjectTemplateBasedTestWizard.createByDirectory(directory, buildSystem, projectDirectory, createWizardTestServiceManager())
}
