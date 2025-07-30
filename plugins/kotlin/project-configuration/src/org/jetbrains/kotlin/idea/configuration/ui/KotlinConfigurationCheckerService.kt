// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration.ui

import com.intellij.facet.ProjectFacetManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportProgress
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.isKotlinLanguageVersionConfigured
import org.jetbrains.kotlin.idea.configuration.getModulesWithKotlinFiles
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.facet.getLibraryLanguageLevel
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.platform.idePlatformKind
import java.util.concurrent.atomic.AtomicInteger

@InternalIgnoreDependencyViolation
private class KotlinConfigurationCheckerStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // do not show the `Configure Kotlin Language Settings` progress bar too early, await initial scanning and indexing
        project.waitForSmartMode()

        KotlinConfigurationCheckerService.getInstance(project).performProjectPostOpenActions()
    }
}

const val KOTLIN_LANGUAGE_VERSION_CONFIGURED_PROPERTY_NAME: String = "kotlin-language-version-configured"

@Service(Service.Level.PROJECT)
class KotlinConfigurationCheckerService(private val project: Project) {
    private val syncDepth = AtomicInteger()

    suspend fun performProjectPostOpenActions() {
        withBackgroundProgress(
            project,
            KotlinProjectConfigurationBundle.message("configure.kotlin.language.settings"),
            TaskCancellation.nonCancellable(),
            null,
            false
        ) {
            doPerformProjectPostOpenActions()
        }
    }

    fun performProjectPostOpenActionsInEdt() {
        runWithModalProgressBlocking(project, KotlinProjectConfigurationBundle.message("configure.kotlin.language.settings.title")) {
            doPerformProjectPostOpenActions()
        }
    }

    private suspend fun doPerformProjectPostOpenActions() {
        val propertiesComponent = PropertiesComponent.getInstance(project)
        if (propertiesComponent.isValueSet(KOTLIN_LANGUAGE_VERSION_CONFIGURED_PROPERTY_NAME)) return

        val kotlinLanguageVersionConfigured = readAction { isKotlinLanguageVersionConfigured(project) }

        val ktModules = if (kotlinLanguageVersionConfigured) {
            // we already have `.idea/kotlinc `, so it's ok to add the jps version there
            KotlinJpsPluginSettings.validateSettings(project)

            // pick up modules with kotlin faces those use custom (non-project) settings
            val modulesWithKotlinFacets = readAction {
                ProjectFacetManager.getInstance(project).getModulesWithFacet(KotlinFacetType.TYPE_ID)
            }
                .filter {
                    val facetSettings = KotlinFacet.get(it)?.configuration?.settings ?: return@filter false
                    // module uses custom (not a project-wide) kotlin facet settings, and LV or ApiVersion is missed
                    !facetSettings.useProjectSettings && (facetSettings.languageLevel == null || facetSettings.apiLevel == null)
                }

            if (modulesWithKotlinFacets.isEmpty()) {
                LOG.debug("Found no Kotlin modules with facets")
                propertiesComponent.setValue(KOTLIN_LANGUAGE_VERSION_CONFIGURED_PROPERTY_NAME, true)
                return
            }

            getModulesWithKotlinFiles(project, modulesWithKotlinFacets)
        } else {
            getModulesWithKotlinFiles(project)
        }

        if (ktModules.isEmpty()) {
            propertiesComponent.setValue(KOTLIN_LANGUAGE_VERSION_CONFIGURED_PROPERTY_NAME, true)
            LOG.debug("Found no Kotlin modules")
            return
        }
        if (!kotlinLanguageVersionConfigured) {
            KotlinJpsPluginSettings.validateSettings(project)
        }

        val writeActionContinuations = mutableListOf<() -> Unit>()
        reportProgress(ktModules.size) { reporter ->
            ktModules.forEach { module ->
                reporter.itemStep(KotlinProjectConfigurationBundle.message("configure.kotlin.language.settings.0.module", module.name)) {
                    readAction {
                        if (module.isDisposed) {
                            return@readAction
                        }
                        getAndCacheLanguageLevelByDependencies(module, writeActionContinuations)
                    }
                }
            }
        }
        if (writeActionContinuations.isNotEmpty()) {
            edtWriteAction {
                writeActionContinuations.forEach { it.invoke() }
            }
        }
        propertiesComponent.setValue(KOTLIN_LANGUAGE_VERSION_CONFIGURED_PROPERTY_NAME, true)
        LOG.debug("Kotlin language version configured successfully")
    }

    @IntellijInternalApi
    fun getAndCacheLanguageLevelByDependencies(module: Module, writeActionContinuations: MutableList<() -> Unit>) {
        val facetSettings = KotlinFacetSettingsProvider.getInstance(project)?.getInitializedSettings(module) ?: return
        val newLanguageLevel = getLibraryLanguageLevel(module, null, facetSettings.targetPlatform?.idePlatformKind)

        // Preserve the inferred version in facet/project settings
        if (facetSettings.useProjectSettings) {
            KotlinCommonCompilerArgumentsHolder.getInstance(project).takeUnless { isKotlinLanguageVersionConfigured(it) }
                ?.let { compilerArgumentsHolder ->
                    writeActionContinuations += {
                        compilerArgumentsHolder.update {
                            if (languageVersion == null) {
                                languageVersion = newLanguageLevel.versionString
                            }

                            if (apiVersion == null) {
                                apiVersion = newLanguageLevel.versionString
                            }
                        }

                    }
                }
        } else {
            with(facetSettings) {
                if (languageLevel == null) {
                    languageLevel = newLanguageLevel
                }

                if (apiLevel == null) {
                    apiLevel = newLanguageLevel
                }
            }
        }
    }

    val isSyncing: Boolean get() = syncDepth.get() > 0

    fun syncStarted() {
        syncDepth.incrementAndGet()
    }

    fun syncDone() {
        syncDepth.decrementAndGet()
    }

    companion object {
        private val LOG = logger<KotlinConfigurationCheckerService>()

        fun getInstance(project: Project): KotlinConfigurationCheckerService = project.service()
    }
}
