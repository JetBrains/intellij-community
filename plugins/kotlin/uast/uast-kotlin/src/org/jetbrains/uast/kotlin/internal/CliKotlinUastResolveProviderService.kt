// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.internal

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService

class CliKotlinUastResolveProviderService : KotlinUastResolveProviderService {
    private val Project.analysisCompletedHandler: UastAnalysisHandlerExtension?
        get() {
            return extensionArea.getExtensionPoint(AnalysisHandlerExtension.extensionPointName).extensionList
                .filterIsInstance<UastAnalysisHandlerExtension>()
                .firstOrNull()
        }

    @Deprecated(
        "Do not use the old frontend, retroactively named as FE1.0, since K2 with the new frontend is coming.\n" +
                "Please use analysis API: https://github.com/JetBrains/kotlin/blob/master/docs/analysis/analysis-api/analysis-api.md",
        replaceWith = ReplaceWith("analyze(element) { }", "org.jetbrains.kotlin.analysis.api.analyze")
    )
    override fun getBindingContext(element: KtElement): BindingContext {
        return element.project.analysisCompletedHandler?.getBindingContext() ?: BindingContext.EMPTY
    }

    override fun isJvmOrCommonElement(psiElement: PsiElement): Boolean = true

    override fun getLanguageVersionSettings(element: KtElement): LanguageVersionSettings {
        return element.project.analysisCompletedHandler?.getLanguageVersionSettings() ?: LanguageVersionSettingsImpl.DEFAULT
    }
}

class UastAnalysisHandlerExtension : AnalysisHandlerExtension {
    private var context: BindingContext? = null
    private var typeMapper: KotlinTypeMapper? = null
    private var languageVersionSettings: LanguageVersionSettings? = null

    fun getBindingContext() = context

    fun getLanguageVersionSettings() = languageVersionSettings

    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider
    ): AnalysisResult? {
        languageVersionSettings = componentProvider.get<LanguageVersionSettings>()
        return super.doAnalysis(project, module, projectContext, files, bindingTrace, componentProvider)
    }

    override fun analysisCompleted(
            project: Project,
            module: ModuleDescriptor,
            bindingTrace: BindingTrace,
            files: Collection<KtFile>
    ): AnalysisResult? {
        context = bindingTrace.bindingContext
        return null
    }
}
