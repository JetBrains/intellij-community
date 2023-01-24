// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.k2.service

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.inspections.EnumValuesSoftDeprecateInJavaInspection
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.BaseInspectionWizardService

internal class IdeaInspectionsWizardService(project: Project) : BaseInspectionWizardService(project) {
    override val inspectionsToEnable = listOf(
        ::EnumValuesSoftDeprecateInJavaInspection to HighlightDisplayLevel.WARNING,
    )
}
