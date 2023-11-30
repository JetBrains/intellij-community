// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core.service

import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.TaskResult
import org.jetbrains.kotlin.tools.projectWizard.core.UNIT_SUCCESS
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.ModuleIR
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemSettings
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import java.nio.file.Path

interface ProjectImportingWizardService : BuildSystemWizardService {
    fun importProject(
        reader: Reader,
        path: Path,
        modulesIrs: List<ModuleIR>,
        buildSystem: BuildSystemType,
        buildSystemSettings: BuildSystemSettings?
    ): TaskResult<Unit>
}

class ProjectImportingWizardServiceImpl : ProjectImportingWizardService, IdeaIndependentWizardService {
    override fun isSuitableFor(buildSystemType: BuildSystemType): Boolean = true
    override fun importProject(
        reader: Reader,
        path: Path,
        modulesIrs: List<ModuleIR>,
        buildSystem: BuildSystemType,
        buildSystemSettings: BuildSystemSettings?
    ) = UNIT_SUCCESS
}
