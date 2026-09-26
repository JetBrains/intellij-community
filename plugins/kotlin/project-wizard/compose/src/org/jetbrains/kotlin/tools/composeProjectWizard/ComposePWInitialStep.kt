// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.composeProjectWizard

import com.intellij.ide.starters.local.StarterContextProvider
import com.intellij.ide.starters.local.wizard.StarterInitialStep
import com.intellij.ide.starters.shared.hyperLink
import com.intellij.ui.dsl.builder.Panel

private const val DESKTOP_TEMPLATE_LINK = "https://jb.gg/idea-wizard-compose-desktop-template"
private const val MULTIPLATFORM_WIZARD_LINK = "https://kotl.in/idea-compose-wizard"

class ComposePWInitialStep(contextProvider: StarterContextProvider) : StarterInitialStep(contextProvider) {

    override fun addFieldsAfter(layout: Panel) {
        layout.row {
            hyperLink(ComposeProjectWizardBundle.message("compose.desktop.tutorial"), DESKTOP_TEMPLATE_LINK)
        }
        layout.separator()
        layout.row {
            comment(
                comment = ComposeProjectWizardBundle.message("compose.multiplatform.wizard", MULTIPLATFORM_WIZARD_LINK),
                maxLineLength = 50
            )
        }
    }

}