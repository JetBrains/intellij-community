// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.withCurrentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.ui.Messages
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.psi.PsiJavaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurationService
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator
import org.jetbrains.kotlin.idea.configuration.getAbleToRunConfigurators
import org.jetbrains.kotlin.idea.configuration.hasKotlinPluginEnabled
import org.jetbrains.kotlin.idea.configuration.isModuleConfigured
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.nj2k.KotlinJ2KK2Bundle
import org.jetbrains.kotlin.platform.jvm.isJvm
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class J2KKotlinConfigurationService(val project: Project) {
    fun checkKotlinIsConfigured(module: Module): Boolean {
        return module.hasKotlinPluginEnabled() || isModuleConfigured(module.toModuleGroup())
    }

    fun setUpAndConvert(
        module: Module,
        javaFiles: List<PsiJavaFile>,
        convertFunction: (List<PsiJavaFile>, Project, Module) -> Unit
    ) {
        val title = KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.title")
        if (isUnitTestMode() || Messages.showOkCancelDialog(
                project,
                KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.message"),
                title,
                KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.configure"),
                KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.cancel.conversion"),
                Messages.getWarningIcon()
            ) == Messages.OK
        ) {
            val configurators = getAbleToRunConfigurators(module).filter { it.targetPlatform.isJvm() }
            when {
                configurators.isEmpty() -> {
                    val message = KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.no.configurators.available")
                    Messages.showErrorDialog(message, title)
                    return
                }

                configurators.size == 1 -> {
                    val configurator = configurators.single()
                    configureKotlinAndConvertJavaCodeToKotlin(
                        configurator,
                        module,
                        convertFunction,
                        javaFiles
                    )
                }

                else -> {
                    @Suppress("DEPRECATION")
                    val resultIndex = Messages.showChooseDialog( //TODO a better dialog?
                        project,
                        KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.choose.configurator"),
                        title,
                        null,
                        configurators.map { it.presentableText }.toTypedArray(),
                        configurators.first().presentableText
                    )
                    val configurator = configurators.getOrNull(resultIndex) ?: return
                    configureKotlinAndConvertJavaCodeToKotlin(
                        configurator,
                        module,
                        convertFunction,
                        javaFiles
                    )
                }
            }
        }
    }

    suspend fun ensureKotlinConfigured(module: Module): Boolean {
        if (isUnitTestMode() || checkKotlinIsConfigured(module)) return true

        val title = KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.title")
        val confirmed = withContext(Dispatchers.EDT) {
            isUnitTestMode() || Messages.showOkCancelDialog(
                project,
                KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.message"),
                title,
                KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.configure"),
                KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.cancel.conversion"),
                Messages.getWarningIcon()
            ) == Messages.OK
        }
        if (!confirmed) return false

        val configurators = getAbleToRunConfigurators(module).filter { it.targetPlatform.isJvm() }
        val configurator = withContext(Dispatchers.EDT) {
            when {
                configurators.isEmpty() -> {
                    Messages.showErrorDialog(
                        KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.no.configurators.available"),
                        title
                    )
                    null
                }

                configurators.size == 1 -> configurators.single()

                else -> {
                    @Suppress("DEPRECATION")
                    val resultIndex = Messages.showChooseDialog(
                        project,
                        KotlinJ2KK2Bundle.message("converter.kotlin.not.configured.choose.configurator"),
                        title,
                        null,
                        configurators.map { it.presentableText }.toTypedArray(),
                        configurators.first().presentableText
                    )
                    configurators.getOrNull(resultIndex)
                }
            }
        } ?: return false

        configureKotlin(configurator, module)
        return true
    }

    private suspend fun configureKotlin(configurator: KotlinProjectConfigurator, module: Module) {
        val configurationService = KotlinProjectConfigurationService.getInstance(module.project)
        val autoConfigured = configurationService.autoConfigure(module)

        withContext(Dispatchers.EDT) {
            if (!autoConfigured) {
                val excludeModules = project.modules.filter { it != module }
                configurator.configureAndGetConfiguredModules(project, excludeModules)
                configurator.queueSyncAndWaitForProjectToBeConfigured(project)
            }
        }
    }

    private fun configureKotlinAndConvertJavaCodeToKotlin(
        configurator: KotlinProjectConfigurator,
        module: Module,
        convertFunction: (List<PsiJavaFile>, Project, Module) -> Unit,
        javaFiles: List<PsiJavaFile>
    ) {
        val configurationService = KotlinProjectConfigurationService.getInstance(module.project)
        val job = configurationService.coroutineScope.launchTracked(Dispatchers.Default) {
            configureKotlin(configurator, module)

            withContext(Dispatchers.EDT) {
                withCurrentThreadCoroutineScope {
                    convertFunction(javaFiles, project, module)
                }
            }
        }
        jobReference?.set(job)
    }

    @VisibleForTesting
    var jobReference: AtomicReference<Job>? = null
}