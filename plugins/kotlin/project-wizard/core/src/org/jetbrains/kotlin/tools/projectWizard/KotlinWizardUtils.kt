// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.BrowserUtil
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Kotlin.logKmpWizardLinkClicked
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun NewProjectWizardStep.addMultiPlatformLink(builder: Panel) {
    builder.row {
        comment(
            KotlinNewProjectWizardBundle.message(
                "multiplatform.web.wizard.comment",
                KotlinNewProjectWizardBundle.message("multiplatform.web.wizard.link")
            )
        ) {
            logKmpWizardLinkClicked()
            BrowserUtil.browse(it.url)
        }
    }.topGap(TopGap.SMALL)
}