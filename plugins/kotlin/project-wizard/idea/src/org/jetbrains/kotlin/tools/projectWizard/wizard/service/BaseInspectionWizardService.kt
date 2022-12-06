// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard.service

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import org.jetbrains.kotlin.tools.projectWizard.core.service.InspectionWizardService

abstract class BaseInspectionWizardService(private val project: Project) : InspectionWizardService, IdeaWizardService {

    protected abstract val inspectionsToEnable: Iterable<Pair<() -> LocalInspectionTool, HighlightDisplayLevel>>

    final override fun changeInspectionSettings() {
        val projectProfile = ProjectInspectionProfileManager.getInstance(project).projectProfile ?: return

        for ((inspectionConstructor, level) in inspectionsToEnable) {
            val inspection = inspectionConstructor.invoke()
            enableInspection(inspection, projectProfile, level)
        }
    }

    private fun enableInspection(inspection: LocalInspectionTool, projectProfile: String, level: HighlightDisplayLevel) {
        val key = HighlightDisplayKey.find(inspection.shortName) ?: return
        val profile = InspectionProfileManager.getInstance(project).getProfile(projectProfile)
        profile.setErrorLevel(key, level, project)
        profile.enableTool(inspection.shortName, project)
    }
}