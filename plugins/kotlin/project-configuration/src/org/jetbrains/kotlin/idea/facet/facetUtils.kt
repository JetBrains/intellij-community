// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.facet

import com.intellij.facet.FacetManager
import com.intellij.model.SideEffectGuard
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.base.projectStructure.ExternalCompilerVersionProvider
import org.jetbrains.kotlin.idea.base.util.invalidateProjectRoots
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform

val mavenLibraryIdToPlatform: Map<String, IdePlatformKind> by lazy {
    IdePlatformKind.ALL_KINDS
        .flatMap { platform -> platform.tooling.mavenLibraryIds.map { it to platform } }
        .sortedByDescending { it.first.length }
        .toMap()
}

fun Module.getOrCreateFacet(
    modelsProvider: IdeModifiableModelsProvider,
    useProjectSettings: Boolean,
    externalSystemId: String? = null,
    commitModel: Boolean = false
): KotlinFacet {
    return getOrCreateConfiguredFacet(modelsProvider, useProjectSettings, externalSystemId, commitModel)
}

fun Module.getOrCreateConfiguredFacet(
    modelsProvider: IdeModifiableModelsProvider,
    useProjectSettings: Boolean,
    externalSystemId: String? = null,
    commitModel: Boolean = false,
    configure: KotlinFacet.() -> Unit = {}
): KotlinFacet {
    val facetModel = modelsProvider.getModifiableFacetModel(this)

    val facet = facetModel.findFacet(KotlinFacetType.TYPE_ID, KotlinFacetType.INSTANCE.defaultFacetName)
        ?: with(KotlinFacetType.INSTANCE) { createFacet(this@getOrCreateConfiguredFacet, defaultFacetName, createDefaultConfiguration(), null) }
            .apply {
                val externalSource = externalSystemId?.let { ExternalProjectSystemRegistry.getInstance().getSourceById(it) }
                facetModel.addFacet(this, externalSource)
            }
    facet.externalSource = externalSystemId?.let { ExternalProjectSystemRegistry.getInstance().getSourceById(it) }
    facet.configuration.settings.useProjectSettings = useProjectSettings
    facet.configure()
    if (commitModel) {
        runWriteAction {
            facetModel.commit()
        }
    }
    return facet
}

fun Module.removeKotlinFacet(
    modelsProvider: IdeModifiableModelsProvider,
    commitModel: Boolean = false
) {
    val facetModel = modelsProvider.getModifiableFacetModel(this)
    val facet = facetModel.findFacet(KotlinFacetType.TYPE_ID, KotlinFacetType.INSTANCE.defaultFacetName) ?: return
    facetModel.removeFacet(facet)
    if (commitModel) {
        runWriteAction {
            facetModel.commit()
        }
    }
}

@JvmOverloads
fun KotlinFacet.configureFacet(
    compilerVersion: IdeKotlinVersion?,
    platform: TargetPlatform?, // if null, detect by module dependencies
    modelsProvider: IdeModifiableModelsProvider,
    hmppEnabled: Boolean = false,
    pureKotlinSourceFolders: List<String> = emptyList(),
    dependsOnList: List<String> = emptyList(),
    additionalVisibleModuleNames: Set<String> = emptySet()
) {
    val module = module
    with(configuration.settings) {
        this.compilerArguments = null
        this.targetPlatform = null
        this.compilerSettings = null
        this.isHmppEnabled = hmppEnabled
        this.dependsOnModuleNames = dependsOnList
        this.additionalVisibleModuleNames = additionalVisibleModuleNames
        initializeIfNeeded(
            module,
            modelsProvider.getModifiableRootModel(module),
            platform,
            compilerVersion
        )
        this.pureKotlinSourceFolders = pureKotlinSourceFolders
    }

    ExternalCompilerVersionProvider.set(module, compilerVersion)
}

fun IKotlinFacetSettings.noVersionAutoAdvance() {
    updateCompilerArguments {
        autoAdvanceLanguageVersion = false
        autoAdvanceApiVersion = false
    }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use IKotlinFacetSettings.noVersionAutoAdvance() instead")
fun KotlinFacet.noVersionAutoAdvance() {
    configuration.settings.updateCompilerArguments {
        autoAdvanceLanguageVersion = false
        autoAdvanceApiVersion = false
    }
}

/**
 * Sets the given [languageVersion] (if non-null) and [apiVersion] (if non-null) in the Kotlin facet settings of this [Module].
 */
// public because it's reused for Amper quick fix as an optimistic optimization (so we have the fix without waiting for a new sync)
fun Module.setLanguageAndApiVersionInKotlinFacet(languageVersion: String?, apiVersion: String?) {
    // prevents this side effect from being actually run from quickfix previews (e.g. in Fleet)
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.PROJECT_MODEL)

    val facetSettings = KotlinFacetSettingsProvider.getInstance(project)?.getSettings(this)
    if (facetSettings != null) {
        // Nikita Bobko: this approach makes the facet changes visible to the build process.
        // Using `createModifiableModel` & `commit` APIs looks weird (I don't use `ModifiableFacetModel` in between of those calls)
        // but it works, contrary to previous `ModuleRootModificationUtil.updateModel`. I don't know why.
        val model = FacetManager.getInstance(this).createModifiableModel()
        with(facetSettings) {
            if (languageVersion != null) {
                languageLevel = LanguageVersion.fromVersionString(languageVersion)
            }
            if (apiVersion != null) {
                apiLevel = LanguageVersion.fromVersionString(apiVersion)
            }
        }
        runWriteAction {
            model.commit()
        }
    }
}

/**
 * Adds the given [compilerArgument] to the Kotlin facet compiler settings of this [Module].
 */
// public because it's reused for Amper quick fix as an optimistic optimization (so we have the fix without waiting for a new sync)
fun Module.addCompilerArgumentToKotlinFacet(compilerArgument: String) {
    // prevents this side effect from being actually run from quickfix previews (e.g. in Fleet)
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.PROJECT_MODEL)

    val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project)
    try {
        getOrCreateConfiguredFacet(modelsProvider, useProjectSettings = false, commitModel = true) {
            val facetSettings = configuration.settings
            val compilerSettings = facetSettings.compilerSettings ?: CompilerSettings().also {
                facetSettings.compilerSettings = it
            }

            compilerSettings.additionalArguments += " $compilerArgument"
            facetSettings.updateMergedArguments()
        }
        project.invalidateProjectRoots(RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
    } finally {
        modelsProvider.dispose()
    }
}
