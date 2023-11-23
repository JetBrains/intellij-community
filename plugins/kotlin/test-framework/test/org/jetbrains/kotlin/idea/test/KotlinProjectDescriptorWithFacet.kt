// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.facet.*
import org.jetbrains.kotlin.idea.workspaceModel.KotlinFacetBridgeFactory

class KotlinProjectDescriptorWithFacet(
    private val languageVersion: LanguageVersion,
    private val multiPlatform: Boolean = false
) : KotlinLightProjectDescriptor() {

    private var facetConfig: KotlinFacetConfiguration? = null

    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        configureKotlinFacet(module) {
            facetConfig = this
            toFacetConfig(this)
        }
    }

    fun replicateToFacetSettings() {
        facetConfig?.let { toFacetConfig(it) }
    }

    private fun toFacetConfig(configuration: KotlinFacetConfiguration) {
        configuration.settings.languageLevel = languageVersion
        if (multiPlatform) {
            configuration.settings.compilerSettings = CompilerSettings().apply {
                additionalArguments += " -Xmulti-platform"
            }
        }
    }

    companion object {
        val KOTLIN_10 = KotlinProjectDescriptorWithFacet(LanguageVersion.KOTLIN_1_0)
        val KOTLIN_11 = KotlinProjectDescriptorWithFacet(LanguageVersion.KOTLIN_1_1)
        val KOTLIN_STABLE_WITH_MULTIPLATFORM =
            KotlinProjectDescriptorWithFacet(KotlinPluginLayout.standaloneCompilerVersion.languageVersion, multiPlatform = true)

        fun createKotlinWithMultiplatformAndStdlib() = object : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
            override fun configureModule(module: Module, model: ModifiableRootModel) {
                configureKotlinFacet(module) {
                    settings.languageLevel = KotlinPluginLayout.standaloneCompilerVersion.languageVersion
                    settings.compilerSettings = CompilerSettings().apply {
                        additionalArguments += " -Xmulti-platform"
                    }
                }
                super.configureModule(module, model)
            }
        }
    }
}

fun configureKotlinFacet(module: Module, configureCallback: KotlinFacetConfiguration.() -> Unit = {}): KotlinFacet {
    val facetManager = FacetManager.getInstance(module)
    val facetModel = facetManager.createModifiableModel()
    val configuration = KotlinFacetBridgeFactory.createFacetConfiguration()
    configuration.settings.initializeIfNeeded(module, null)
    configuration.settings.useProjectSettings = false
    configuration.configureCallback()
    val facet = facetManager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", configuration, null)
    facetModel.addFacet(facet)
    runInEdtAndWait {
        runWriteAction { facetModel.commit() }
    }
    return facet
}

