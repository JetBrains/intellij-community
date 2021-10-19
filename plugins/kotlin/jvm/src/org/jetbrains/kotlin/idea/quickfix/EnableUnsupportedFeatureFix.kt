// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import org.eclipse.aether.version.Version
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.isStableOrReadyForPreview
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.findApplicableConfigurator
import org.jetbrains.kotlin.idea.configuration.getBuildSystemType
import org.jetbrains.kotlin.idea.core.isInTestSourceContentKotlinAware
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.roots.invalidateProjectRoots
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import org.jetbrains.kotlin.idea.versions.LibraryJarDescriptor
import org.jetbrains.kotlin.idea.versions.findKotlinRuntimeLibrary
import org.jetbrains.kotlin.idea.versions.updateLibraries
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

    class InModule(element: PsiElement, feature: LanguageFeature, apiVersionOnly: Boolean) :
        EnableUnsupportedFeatureFix(element, feature, apiVersionOnly, isModule = true) {
        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return

            val facetSettings = KotlinFacetSettingsProvider.getInstance(project)?.getInitializedSettings(module)
            val targetApiLevel = facetSettings?.apiLevel?.let { apiLevel ->
                if (ApiVersion.createByLanguageVersion(apiLevel) < feature.sinceApiVersion)
                    feature.sinceApiVersion.versionString
                else
                    null
            }
            val forTests = ModuleRootManager.getInstance(module).fileIndex.isInTestSourceContentKotlinAware(file.virtualFile)

            findApplicableConfigurator(module).updateLanguageVersion(
                module,
                if (apiVersionOnly) null else feature.sinceVersion!!.versionString,
                targetApiLevel,
                feature.sinceApiVersion,
                forTests
            )
        }
    }

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
            project.invalidateProjectRoots()
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
            if (module.getBuildSystemType() == BuildSystemType.JPS) {
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

fun checkUpdateRuntime(project: Project, requiredVersion: ApiVersion): Boolean {
    val modulesWithOutdatedRuntime = project.allModules().filter { module ->
        val parsedModuleRuntimeVersion = getRuntimeLibraryVersion(module)?.let { version ->
            ApiVersion.parse(version.substringBefore("-"))
        }
        parsedModuleRuntimeVersion != null && parsedModuleRuntimeVersion < requiredVersion
    }
    if (modulesWithOutdatedRuntime.isNotEmpty()) {
        if (!askUpdateRuntime(project, requiredVersion,
                              modulesWithOutdatedRuntime.mapNotNull { findKotlinRuntimeLibrary(it) })
        ) return false
    }
    return true
}

fun askUpdateRuntime(project: Project, requiredVersion: ApiVersion, librariesToUpdate: List<Library>): Boolean {
    if (!isUnitTestMode()) {
        val rc = Messages.showOkCancelDialog(
            project,
            KotlinJvmBundle.message(
                "this.language.feature.requires.version.0.or.later.of.the.kotlin.runtime.library.would.you.like.to.update.the.runtime.library.in.your.project",
                requiredVersion
            ),
            KotlinJvmBundle.message("update.runtime.library"),
            Messages.getQuestionIcon()
        )
        if (rc != Messages.OK) return false
    }

    val upToMavenVersion = requiredVersion.toMavenArtifactVersion(project) ?: run {
        Messages.showErrorDialog(
            KotlinJvmBundle.message("cant.fetch.available.maven.versions"),
            KotlinJvmBundle.message("cant.fetch.available.maven.versions.title")
        )
        return false
    }
    updateLibraries(project, upToMavenVersion, librariesToUpdate)
    return true
}

internal fun ApiVersion.toMavenArtifactVersion(project: Project): String? {
    val apiVersion = this
    var mavenVersion: String? = null
    object : Task.Modal(project, KotlinJvmBundle.message("fetching.available.maven.versions.title"), true) {
        override fun run(indicator: ProgressIndicator) {
            val repositoryLibraryProperties = LibraryJarDescriptor.RUNTIME_JDK8_JAR.repositoryLibraryProperties
            val version: Version? = ArtifactRepositoryManager(JarRepositoryManager.getLocalRepositoryPath()).getAvailableVersions(
                repositoryLibraryProperties.groupId,
                repositoryLibraryProperties.artifactId,
                "[${apiVersion.versionString},)",
                ArtifactKind.ARTIFACT
            ).firstOrNull()
            mavenVersion = version?.toString()
        }
    }.queue()
    return mavenVersion
}

fun askUpdateRuntime(module: Module, requiredVersion: ApiVersion): Boolean {
    val library = findKotlinRuntimeLibrary(module) ?: return true
    return askUpdateRuntime(module.project, requiredVersion, listOf(library))
}
