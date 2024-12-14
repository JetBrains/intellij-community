// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.ide.projectWizard.generators.AssetsJava
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.icon
import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.shortcut
import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.shouldRenderOnboardingTips
import com.intellij.ide.projectWizard.generators.prepareOnboardingTips
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.keymap.KeymapTextContext
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle

private const val DEFAULT_FILE_NAME = "Main.kt"
private const val DEFAULT_TEMPLATE_NAME = "KotlinSampleCode"
private const val DEFAULT_TEMPLATE_WITH_ONBOARDING_TIPS_NAME = "KotlinSampleCodeWithOnboardingTips"
private const val DEFAULT_TEMPLATE_WITH_RENDERED_ONBOARDING_TIPS_NAME = "KotlinSampleCodeWithRenderedOnboardingTips"

object AssetsKotlin {

    fun getKotlinSampleTemplateName(generateOnboardingTips: Boolean): String {
        return when {
            !generateOnboardingTips -> DEFAULT_TEMPLATE_NAME
            shouldRenderOnboardingTips() -> DEFAULT_TEMPLATE_WITH_RENDERED_ONBOARDING_TIPS_NAME
            else -> DEFAULT_TEMPLATE_WITH_ONBOARDING_TIPS_NAME
        }
    }
}

fun AssetsNewProjectWizardStep.withKotlinSampleCode(
    sourceRootPath: String,
    packageName: String?,
    generateOnboardingTips: Boolean,
    shouldOpenFile: Boolean = true
) {
    val templateName = AssetsKotlin.getKotlinSampleTemplateName(generateOnboardingTips)
    val sourcePath = AssetsJava.getJavaSampleSourcePath(sourceRootPath, null, DEFAULT_FILE_NAME)
    withKotlinSampleCode(sourcePath, templateName, packageName, generateOnboardingTips, shouldOpenFile)
}

fun AssetsNewProjectWizardStep.withKotlinSampleCode(
    sourcePath: String,
    templateName: String,
    packageName: String?,
    generateOnboardingTips: Boolean,
    shouldOpenFile: Boolean = true
) {
    addTemplateAsset(sourcePath, templateName, buildMap {
        packageName?.let {
            put("PACKAGE_NAME", it)
        }
        if (generateOnboardingTips) {
            val tipsContext = KeymapTextContext()

            if (shouldRenderOnboardingTips()) {
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

fun AssetsNewProjectWizardStep.prepareKotlinSampleOnboardingTips(project: Project) {
    prepareKotlinSampleOnboardingTips(project, DEFAULT_FILE_NAME)
}

@ApiStatus.Internal
fun AssetsNewProjectWizardStep.prepareKotlinSampleOnboardingTips(
    project: Project,
    fileName: String
) {
    prepareOnboardingTips(project, fileName) { charsSequence ->
        charsSequence.indexOf("println(\"i").takeIf { it >= 0 }
    }
}
