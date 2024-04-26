// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.ide.projectWizard.generators.AssetsOnboardingTipsProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.keymap.KeymapTextContext
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle

private const val generatedFileName = "Main.kt"

abstract class AssetsKotlinNewProjectWizardStep(parent: NewProjectWizardStep) : AssetsOnboardingTipsProjectWizardStep(parent) {
    fun withKotlinSampleCode(
        sourceRootPath: String,
        packageName: String?,
        generateOnboardingTips: Boolean,
        shouldOpenFile: Boolean = true
    ) {
        val renderedOnboardingTips = shouldRenderOnboardingTips()
        val templateName = when {
            !generateOnboardingTips -> "KotlinSampleCode"
            renderedOnboardingTips -> "KotlinSampleCodeWithRenderedOnboardingTips"
            else -> "KotlinSampleCodeWithOnboardingTips"
        }

        val sourcePath = "$sourceRootPath/$generatedFileName"
        addTemplateAsset(sourcePath, templateName, buildMap {
            packageName?.let {
                put("PACKAGE_NAME", it)
            }
            if (generateOnboardingTips) {
                val tipsContext = KeymapTextContext()

                if (renderedOnboardingTips) {
                    //@formatter:off
                    put("RunComment1", KotlinNewProjectWizardBundle.message("onboarding.run.comment.render.1", shortcut(IdeActions.ACTION_DEFAULT_RUNNER)))
                    put("RunComment2", KotlinNewProjectWizardBundle.message("onboarding.run.comment.render.2", icon("AllIcons.Actions.Execute")))

                    put("ShowIntentionComment1", KotlinNewProjectWizardBundle.message("onboarding.show.intention.tip.comment.render.1", shortcut(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)))
                    put("ShowIntentionComment2", KotlinNewProjectWizardBundle.message("onboarding.show.intention.tip.comment.render.2", ApplicationNamesInfo.getInstance().fullProductName))

                    put("DebugComment1", KotlinNewProjectWizardBundle.message("onboarding.debug.comment.render.1", shortcut(IdeActions.ACTION_DEFAULT_DEBUGGER), icon("AllIcons.Debugger.Db_set_breakpoint")))
                    put("DebugComment2", KotlinNewProjectWizardBundle.message("onboarding.debug.comment.render.2", shortcut(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)))
                    //@formatter:on
                } else {
                    //@formatter:off
                    put("RunComment", KotlinNewProjectWizardBundle.message("onboarding.run.comment", tipsContext.getShortcutText(IdeActions.ACTION_DEFAULT_RUNNER)))

                    put("ShowIntentionComment1", KotlinNewProjectWizardBundle.message("onboarding.show.intention.tip.comment.1", tipsContext.getShortcutText(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)))
                    put("ShowIntentionComment2", KotlinNewProjectWizardBundle.message("onboarding.show.intention.tip.comment.2", ApplicationNamesInfo.getInstance().fullProductName))

                    put("DebugComment1", KotlinNewProjectWizardBundle.message("onboarding.debug.comment.1", tipsContext.getShortcutText(IdeActions.ACTION_DEFAULT_DEBUGGER)))
                    put("DebugComment2", KotlinNewProjectWizardBundle.message("onboarding.debug.comment.2", tipsContext.getShortcutText(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)))
                    //@formatter:on
                }
            }
        })
        if (shouldOpenFile) {
            addFilesToOpen(sourcePath)
        }
    }

    fun prepareOnboardingTips(project: Project) {
        prepareOnboardingTips(project, "KotlinSampleCode", generatedFileName) { charsSequence ->
            charsSequence.indexOf("println(\"i").takeIf { it >= 0 }
        }
    }
}