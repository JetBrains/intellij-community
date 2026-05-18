// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.IncompleteModelUtil.isIncompleteModel
import com.intellij.util.indexing.DumbModeAccessType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.projectStructure.hasKotlinJvmRuntime
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProjectConfigurator
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProjectConfigurator.Companion.compilerPluginProjectConfigurators
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

abstract class AbstractKotlinCompilerPluginInspection(protected val kotlinCompilerPluginId: String): LocalInspectionTool() {
    protected fun compilerPluginProjectConfigurators(module: Module): List<KotlinCompilerPluginProjectConfigurator> =
        compilerPluginProjectConfigurators(kotlinCompilerPluginId, module)

    final override fun isAvailableForFile(file: PsiFile): Boolean =
        isAvailableForFile(file) { file, module -> isAvailableForFileInModule(file, module) }

    protected abstract fun isAvailableForFileInModule(ktFile: KtFile, module: Module): Boolean

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        object : KtVisitorVoid() {
            override fun visitKtFile(file: KtFile) {
                super.visitKtFile(file)

                val addCompilerPlugin = isCompilerPluginRequired(file)

                if (addCompilerPlugin) {
                    holder.registerProblem(
                        /* psiElement = */ file,
                        /* descriptionTemplate = */ descriptionTemplate,
                        /* ...fixes =  */ AddCompilerPluginFix()
                    )
                }
            }
        }

    @get:NlsSafe
    protected abstract val descriptionTemplate: String

    @get:NlsSafe
    protected abstract val familyName: String

    protected abstract fun isCompilerPluginRequired(file: KtFile): Boolean

    inner class AddCompilerPluginFix : ModCommandQuickFix() {
        override fun getFamilyName(): @IntentionFamilyName String =
            this@AbstractKotlinCompilerPluginInspection.familyName

        override fun perform(
            project: Project,
            descriptor: ProblemDescriptor
        ): ModCommand {
            val element = descriptor.psiElement
            return KotlinCompilerPluginProvider.addCompilerPluginModCommand(element, kotlinCompilerPluginId)
        }
    }

    companion object {
        @OptIn(KaPlatformInterface::class)
        @ApiStatus.Internal
        fun KaModule.hasCompilerPluginExtension(filter: (FirExtensionRegistrarAdapter) -> Boolean): Boolean =
            findCompilerPluginExtensionOrNull(filter) != null

        @OptIn(KaPlatformInterface::class)
        @ApiStatus.Internal
        fun KaModule.findCompilerPluginExtensionOrNull(filter: (FirExtensionRegistrarAdapter) -> Boolean): FirExtensionRegistrarAdapter? {
            val pluginsProvider =
                KotlinCompilerPluginsProvider.getInstance(project) ?: return null
            val registeredExtensions =
                pluginsProvider.getRegisteredExtensions(this, FirExtensionRegistrarAdapter)
            return registeredExtensions.firstOrNull(filter)
        }

        @ApiStatus.Internal
        fun KtFile.hasCompilerPluginExtension(filter: (FirExtensionRegistrarAdapter) -> Boolean): Boolean =
            findCompilerPluginExtensionOrNull(filter) != null

        @ApiStatus.Internal
        fun KtFile.findCompilerPluginExtensionOrNull(filter: (FirExtensionRegistrarAdapter) -> Boolean): FirExtensionRegistrarAdapter? {
            val module = getKaModule(project, useSiteModule = null).takeIf { it is KaSourceModule } ?: return null
            return module.findCompilerPluginExtensionOrNull(filter)
        }

        @ApiStatus.Internal
        fun isAvailableForFile(file: PsiFile, isAvailableForFileInModule: (KtFile, Module) -> Boolean): Boolean {
            val ktFile = (file as? KtFile)?.takeUnless { it.isCompiled } ?: return false

            if (isIncompleteModel(file)) return false

            val module = ModuleUtilCore.findModuleForFile(ktFile) ?: return false

            val scope = module.getModuleWithDependenciesAndLibrariesScope(true)
            val hasKotlinJvmRuntime = DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
                scope.hasKotlinJvmRuntime(module.project)
            })

            return hasKotlinJvmRuntime && isAvailableForFileInModule(ktFile, module)
        }
    }

}
