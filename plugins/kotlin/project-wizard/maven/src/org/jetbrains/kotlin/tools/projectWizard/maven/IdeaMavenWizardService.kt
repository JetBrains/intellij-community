// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.maven

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.TaskResult
import org.jetbrains.kotlin.tools.projectWizard.core.safe
import org.jetbrains.kotlin.tools.projectWizard.core.service.ProjectImportingWizardService
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.ModuleIR
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemSettings
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaWizardService
import java.nio.file.Path

internal class IdeaMavenWizardService(private val project: Project) : ProjectImportingWizardService, IdeaWizardService {

    override fun isSuitableFor(buildSystemType: BuildSystemType): Boolean =
        buildSystemType == BuildSystemType.Maven

    override fun importProject(
        reader: Reader,
        path: Path,
        modulesIrs: List<ModuleIR>,
        buildSystem: BuildSystemType,
        buildSystemSettings: BuildSystemSettings?
    ): TaskResult<Unit> = safe {
        MavenProjectImporter(project).importProject(path)
    }
}