// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.base.projectStructure.getKotlinSourceRootType
import org.jetbrains.kotlin.idea.base.util.invalidateProjectRoots
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.idea.configuration.findApplicableConfigurator
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.projectConfiguration.checkUpdateRuntime
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import com.intellij.openapi.application.runWriteAction
import org.jetbrains.kotlin.psi.KtFile

sealed class EnableUnsupportedFeatureFix(
    element: PsiElement,
    protected val feature: LanguageFeature,
    protected val apiVersionOnly: Boolean,
    protected val isModule: Boolean,
) : KotlinQuickFixAction<PsiElement>(element) {
    override fun getFamilyName() = KotlinJvmBundle.message(
        "enable.feature.family",
        0.takeIf { isModule } ?: 1,
        0.takeIf { apiVersionOnly } ?: 1
    )

    override fun getText() = KotlinJvmBundle.message(
        "enable.feature.text",
        0.takeIf { isModule } ?: 1,
        0.takeIf { apiVersionOnly } ?: 1,
        if (apiVersionOnly) feature.sinceApiVersion.versionString else feature.sinceVersion?.versionString.toString()
    )

    /**
     * Tests:
     * [org.jetbrains.kotlin.idea.maven.MavenUpdateConfigurationQuickFixTest12]
     * [org.jetbrains.kotlin.idea.codeInsight.gradle.GradleUpdateConfigurationQuickFixTest]
     * [org.jetbrains.kotlin.idea.quickfix.UpdateConfigurationQuickFixTest.testModuleLanguageVersion]
     */
    class InModule(element: PsiElement, feature: LanguageFeature, apiVersionOnly: Boolean) :
        EnableUnsupportedFeatureFix(element, feature, apiVersionOnly, isModule = true) {
        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return

            val facetSettings = KotlinFacetSettingsProvider.getInstance(project)?.getSettings(module)
            val targetApiLevel = facetSettings?.apiLevel?.let { apiLevel ->
                if (ApiVersion.createByLanguageVersion(apiLevel) < feature.sinceApiVersion)
                    feature.sinceApiVersion.versionString
                else
                    null
            }

            val fileIndex = ModuleRootManager.getInstance(module).fileIndex
            val forTests = file.originalFile.virtualFile?.let { fileIndex.getKotlinSourceRootType(it) } == TestSourceKotlinRootType

            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(
                    project,
                    KotlinJvmBundle.message("command.name.update.kotlin.language.version"),
                    null,
                    Runnable {
                        findApplicableConfigurator(module).updateLanguageVersion(
                            module,
                            if (apiVersionOnly) null else feature.sinceVersion!!.versionString,
                            targetApiLevel,
                            feature.sinceApiVersion,
                            forTests
                        )
                        project.invalidateProjectRoots(RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
                    }
                )
            }
        }
    }

    /**
     * Tests:
     * [org.jetbrains.kotlin.idea.quickfix.UpdateConfigurationQuickFixTest.testProjectLanguageVersion]
     */
    class InProject(element: PsiElement, feature: LanguageFeature, apiVersionOnly: Boolean) :
        EnableUnsupportedFeatureFix(element, feature, apiVersionOnly, isModule = false) {
        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val targetVersion = feature.sinceVersion!!

            KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
                val parsedApiVersion = apiVersion?.let { ApiVersion.parse(it) }
                if (parsedApiVersion != null && feature.sinceApiVersion > parsedApiVersion) {
                    if (!checkUpdateRuntime(project, feature.sinceApiVersion)) return@update
                    apiVersion = feature.sinceApiVersion.versionString
                }

                if (!apiVersionOnly) {
                    languageVersion = targetVersion.versionString
                }
            }
            project.invalidateProjectRoots(RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): EnableUnsupportedFeatureFix? {
            val (feature, languageFeatureSettings) = Errors.UNSUPPORTED_FEATURE.cast(diagnostic).a

            val sinceVersion = feature.sinceVersion ?: return null
            val apiVersionOnly = sinceVersion <= languageFeatureSettings.languageVersion &&
                    feature.sinceApiVersion > languageFeatureSettings.apiVersion

            if (!sinceVersion.isStableOrReadyForPreview() && !isApplicationInternalMode()) {
                return null
            }

            val module = ModuleUtilCore.findModuleForPsiElement(diagnostic.psiElement) ?: return null
            if (module.buildSystemType == BuildSystemType.JPS) {
                val facetSettings = KotlinFacet.get(module)?.configuration?.settings
                if (facetSettings == null || facetSettings.useProjectSettings) return InProject(
                    diagnostic.psiElement,
                    feature,
                    apiVersionOnly
                )
            }
            return InModule(diagnostic.psiElement, feature, apiVersionOnly)
        }
    }
}