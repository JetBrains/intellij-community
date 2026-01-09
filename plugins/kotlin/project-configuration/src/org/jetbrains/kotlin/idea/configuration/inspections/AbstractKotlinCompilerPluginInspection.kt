// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProjectConfigurator
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurationService
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

abstract class AbstractKotlinCompilerPluginInspection(protected val kotlinCompilerPluginId: String): LocalInspectionTool() {
    protected fun compilerPluginProjectConfigurators(): List<KotlinCompilerPluginProjectConfigurator> =
        KotlinCompilerPluginProjectConfigurator.EP_NAME.extensionList
            .filter { it.kotlinCompilerPluginId == kotlinCompilerPluginId }

    protected fun KtFile.hasCompilerPluginExtension(filter: (FirExtensionRegistrarAdapter) -> Boolean): Boolean {
        val module = getKaModule(project, useSiteModule = null).takeIf { it is KaSourceModule } ?: return false
        val pluginsProvider =
            KotlinCompilerPluginsProvider.getInstance(project) ?: return false
        val registeredExtensions = pluginsProvider.getRegisteredExtensions(module, FirExtensionRegistrarAdapter)
        return registeredExtensions.any(filter)
    }

    final override fun isAvailableForFile(file: PsiFile): Boolean {
        val ktFile = (file as? KtFile)?.takeUnless { it.isCompiled } ?: return false
        val module = ModuleUtilCore.findModuleForFile(ktFile) ?: return false
        return super.isAvailableForFile(file) && isAvailableForFileInModule(ktFile, module)
    }

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

    protected abstract val descriptionTemplate: String

    protected abstract val familyName: String

    protected abstract fun isCompilerPluginRequired(file: KtFile): Boolean

    inner class AddCompilerPluginFix : LocalQuickFix {
        override fun getFamilyName(): @IntentionFamilyName String =
            this@AbstractKotlinCompilerPluginInspection.familyName

        override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
            IntentionPreviewInfo.EMPTY

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return

            val configurators =
                compilerPluginProjectConfigurators().ifEmpty { return }

            val configurationService = KotlinProjectConfigurationService.getInstance(project)
            configurationService.coroutineScope.launchTracked {
                edtWriteAction {
                    for (configurator in configurators) {
                        configurator.configureModule(module)
                    }
                }
                configurationService.queueSyncIfPossible()
            }
        }
    }

}
