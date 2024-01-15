// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.j2k.post.processing

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
import org.jetbrains.kotlin.idea.configuration.getAbleToRunConfigurators
import org.jetbrains.kotlin.idea.configuration.hasKotlinPluginEnabled
import org.jetbrains.kotlin.idea.configuration.isModuleConfigured
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.nj2k.NewJ2kWithProgressProcessor
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter
import org.jetbrains.kotlin.platform.jvm.isJvm

class NewJ2kConverterExtension : J2kConverterExtension() {
    override val isNewJ2k = true

    override fun createJavaToKotlinConverter(
        project: Project,
        targetModule: Module?,
        settings: ConverterSettings
    ): JavaToKotlinConverter =
        NewJavaToKotlinConverter(project, targetModule, settings)

    override fun createPostProcessor(formatCode: Boolean): PostProcessor =
        NewJ2kPostProcessor()

    override fun doCheckBeforeConversion(project: Project, module: Module): Boolean =
        checkKotlinIsConfigured(project, module)

    override fun createWithProgressProcessor(
        progress: ProgressIndicator?,
        files: List<PsiJavaFile>?,
        phasesCount: Int
    ): WithProgressProcessor =
        NewJ2kWithProgressProcessor(progress, files, phasesCount)

    private fun checkKotlinIsConfigured(project: Project, module: Module): Boolean {
        val kotlinIsConfigured =
            module.hasKotlinPluginEnabled() || isModuleConfigured(module.toModuleGroup())
        if (kotlinIsConfigured) return true

        val title = KotlinNJ2KServicesBundle.message("converter.kotlin.not.configured.title")
        if (Messages.showOkCancelDialog(
                project,
                KotlinNJ2KServicesBundle.message("converter.kotlin.not.configured.message"),
                title,
                KotlinNJ2KServicesBundle.message("converter.kotlin.not.configured.configure"),
                KotlinNJ2KServicesBundle.message("converter.kotlin.not.configured.cancel.conversion"),
                Messages.getWarningIcon()
            ) == Messages.OK
        ) {
            val configurators = getAbleToRunConfigurators(module).filter { it.targetPlatform.isJvm() }
            when {
                configurators.isEmpty() -> {
                    val message = KotlinNJ2KServicesBundle.message("converter.kotlin.not.configured.no.configurators.available")
                    Messages.showErrorDialog(message, title)
                }

                configurators.size == 1 -> configurators.single().configure(project, emptyList())
                else -> {
                    @Suppress("DEPRECATION")
                    val resultIndex = Messages.showChooseDialog( //TODO a better dialog?
                        project,
                        KotlinNJ2KServicesBundle.message("converter.kotlin.not.configured.choose.configurator"),
                        title,
                        null,
                        configurators.map { it.presentableText }.toTypedArray(),
                        configurators.first().presentableText
                    )
                    configurators.getOrNull(resultIndex)?.configure(project, emptyList())
                }
            }
        }

        return false
    }
}