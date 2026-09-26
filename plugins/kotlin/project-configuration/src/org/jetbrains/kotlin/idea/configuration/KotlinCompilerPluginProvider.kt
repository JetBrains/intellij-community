// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.codeInspection.options.OptionController
import com.intellij.codeInspection.options.OptionControllerProvider
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCompositeCommand
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProjectConfigurator.Companion.compilerPluginProjectConfigurators
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle

@ApiStatus.Internal
class KotlinCompilerPluginProvider : OptionControllerProvider {
    override fun forContext(context: PsiElement): OptionController {
        val project = context.project
        return OptionController.empty()
            .onValue<String>(
                "compilerPluginId",
                {
                    ""
                },
                setter@{ compilerPluginId ->
                    val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return@setter
                    configureCompilerPlugin(project, module, compilerPluginId)
                })
    }

    override fun name(): @NonNls String = NAME

    private fun configureCompilerPlugin(project: Project, module: Module, kotlinCompilerPluginId: String) {
        val configurators =
            compilerPluginProjectConfigurators(kotlinCompilerPluginId, module).ifEmpty { return }

        val configurationResultBuilder = ConfigurationResultBuilder()
        val configurationService = KotlinProjectConfigurationService.getInstance(project)
        configurationService.coroutineScope.launchTracked {
            edtWriteAction {
                executeCommand(
                    project,
                    KotlinProjectConfigurationBundle.message("command.name.configure.kotlin.compiler.plugin.0", kotlinCompilerPluginId)
                ) {
                    for (configurator in configurators) {
                        configurator.configureModule(module, configurationResultBuilder)
                    }
                }
                val result = configurationResultBuilder.build()
                if (result.configuredModules.isNotEmpty()) {
                    configurationService.queueSyncIfPossible()
                }
            }
        }
    }

    companion object {
        const val NAME: String = "KotlinCompilerPlugin"

        @JvmStatic
        fun addCompilerPluginOptionModCommand(element: PsiElement, kotlinCompilerPluginId: String): ModCommand {
            return ModCommand.updateOption(element, "$NAME.compilerPluginId", kotlinCompilerPluginId)
        }

        @JvmStatic
        fun addCompilerPluginModCommand(element: PsiElement, kotlinCompilerPluginId: String): ModCommand {
            val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return ModCommand.nop()
            val configurators =
                compilerPluginProjectConfigurators(kotlinCompilerPluginId, module).ifEmpty { return ModCommand.nop() }

            val modCommands = configurators.mapNotNull { configurator ->
                val command = configurator.configureModuleModCommand(module)
                command.takeIf { it != ModCommand.nop() }
            }

            return when(modCommands.size) {
                0 -> addCompilerPluginOptionModCommand(element, kotlinCompilerPluginId)
                1 -> modCommands.first()
                else -> ModCompositeCommand(modCommands)
            }
        }

    }

}
