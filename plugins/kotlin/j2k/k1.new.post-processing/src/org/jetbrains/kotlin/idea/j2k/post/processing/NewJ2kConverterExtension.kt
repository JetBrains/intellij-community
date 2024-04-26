// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiJavaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator
import org.jetbrains.kotlin.idea.configuration.getAbleToRunConfigurators
import org.jetbrains.kotlin.idea.configuration.hasKotlinPluginEnabled
import org.jetbrains.kotlin.idea.configuration.isModuleConfigured
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtFile

class NewJ2kConverterExtension : J2kConverterExtension() {
    override val kind: Kind = K1_NEW

    override fun createJavaToKotlinConverter(
        project: Project,
        targetModule: Module?,
        settings: ConverterSettings,
        targetFile: KtFile?
    ): JavaToKotlinConverter =
        NewJavaToKotlinConverter(project, targetModule, settings, targetFile)

    override fun createPostProcessor(formatCode: Boolean): PostProcessor =
        NewJ2kPostProcessor()

    override fun doCheckBeforeConversion(project: Project, module: Module): Boolean =
        checkKotlinIsConfigured(module)

    override fun createWithProgressProcessor(
        progress: ProgressIndicator?,
        files: List<PsiJavaFile>?,
        phasesCount: Int
    ): WithProgressProcessor =
        NewJ2kWithProgressProcessor(progress, files, phasesCount)

    override fun getConversions(context: NewJ2kConverterContext): List<Conversion> =
        getNewJ2KConversions(context)

    private fun checkKotlinIsConfigured(module: Module): Boolean {
        return module.hasKotlinPluginEnabled() || isModuleConfigured(module.toModuleGroup())
    }

    override fun setUpAndConvert(
        project: Project,
        module: Module,
        javaFiles: List<PsiJavaFile>,
        convertFunction: (List<PsiJavaFile>, Project, Module) -> Unit
    ) {
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
                    return
                }

                configurators.size == 1 -> {
                    val configurator = configurators.single()
                    configureKotlinAndConvertJavaCodeToKotlin(
                        configurator,
                        project,
                        module,
                        convertFunction,
                        javaFiles
                    )
                }

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
                    val configurator = configurators.getOrNull(resultIndex) ?: return
                    configureKotlinAndConvertJavaCodeToKotlin(
                        configurator,
                        project,
                        module,
                        convertFunction,
                        javaFiles
                    )
                }
            }
        }
    }

    private fun configureKotlinAndConvertJavaCodeToKotlin(
        configurator: KotlinProjectConfigurator,
        project: Project,
        module: Module,
        convertFunction: (List<PsiJavaFile>, Project, Module) -> Unit,
        javaFiles: List<PsiJavaFile>
    ) {
        val configuredModules = configurator.configureAndGetConfiguredModules(project, excludeModules = emptyList())
        CoroutineScopeService.getCoroutineScope(project).launch {
            withBackgroundProgress(project, KotlinNJ2KServicesBundle.message("converter.kotlin.wait.for.sync.to.be.finished")) {
                configurator.queueSyncAndWaitForProjectToBeConfigured(project)
            }
            if (configuredModules.contains(module) ||
                /*
                * This is needed because when configuring Kotlin with Gradle we receive a source root module like `myModule.main` but we need
                * just the base module `myModule` because the configurator itself returns a collection of configured base modules.
                */
                configuredModules.contains(module.toModuleGroup().baseModule)
            ) {
                withContext(Dispatchers.EDT) {
                    convertFunction(javaFiles, project, module)
                }
            }
        }
    }

    @Service(Service.Level.PROJECT)
    private class CoroutineScopeService(val coroutineScope: CoroutineScope) {
        companion object {
            fun getCoroutineScope(project: Project): CoroutineScope {
                return project.service<CoroutineScopeService>().coroutineScope
            }
        }
    }
}