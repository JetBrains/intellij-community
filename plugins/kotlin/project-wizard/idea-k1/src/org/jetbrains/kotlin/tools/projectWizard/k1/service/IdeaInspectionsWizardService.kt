// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.k1.service

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ReplaceUntilWithRangeUntilInspection
import org.jetbrains.kotlin.tools.projectWizard.core.service.InspectionWizardService
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaWizardService

class IdeaInspectionsWizardService(private val project: Project) : InspectionWizardService, IdeaWizardService {
    override fun changeInspectionSettings() {
        val projectProfile = ProjectInspectionProfileManager.getInstance(project).projectProfile
        val key = HighlightDisplayKey.find(ReplaceUntilWithRangeUntilInspection().shortName)
        if (projectProfile != null && key != null) {
            InspectionProfileManager.getInstance(project).getProfile(projectProfile)
                .setErrorLevel(key, HighlightDisplayLevel.WEAK_WARNING, project)
        }
    }
}
