// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.core.service

interface InspectionWizardService : WizardService {
    fun changeInspectionSettings()
}

/**
 * When the wizard is run not inside IDE then we don't configure inspections
 */
class EmptyInspectionWizardService : InspectionWizardService, IdeaIndependentWizardService {
    override fun changeInspectionSettings() = Unit
}
