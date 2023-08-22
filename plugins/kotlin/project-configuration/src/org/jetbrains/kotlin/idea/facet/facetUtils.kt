// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.facet

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.base.projectStructure.ExternalCompilerVersionProvider
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
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
        val apiLevel = apiLevel
        val languageLevel = languageLevel
        if (languageLevel != null && apiLevel != null && apiLevel > languageLevel) {
            this.apiLevel = languageLevel
        }
        this.pureKotlinSourceFolders = pureKotlinSourceFolders
    }

    ExternalCompilerVersionProvider.set(module, compilerVersion)
}

fun KotlinFacet.noVersionAutoAdvance() {
    configuration.settings.compilerArguments?.let {
        it.autoAdvanceLanguageVersion = false
        it.autoAdvanceApiVersion = false
    }
}
