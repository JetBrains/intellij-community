// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.platform.impl

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SmartList
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.tooling.IdePlatformKindTooling
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.framework.JSLibraryStdDescription
import org.jetbrains.kotlin.idea.js.KotlinJSRunConfigurationData
import org.jetbrains.kotlin.idea.js.KotlinJSRunConfigurationDataProvider
import org.jetbrains.kotlin.idea.platform.getGenericTestIcon
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.run.multiplatform.KotlinMultiplatformRunLocationsProvider
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.utils.PathUtil
import org.jetbrains.kotlin.utils.ifEmpty
import javax.swing.Icon

class JsIdePlatformKindTooling : IdePlatformKindTooling() {
    companion object {
        private const val MAVEN_OLD_JS_STDLIB_ID = "kotlin-js-library"
    }

    override val kind = JsIdePlatformKind

    override val mavenLibraryIds = listOf(PathUtil.JS_LIB_NAME, MAVEN_OLD_JS_STDLIB_ID)
    override val gradlePluginId = "kotlin-platform-js"
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.JS)

    override val libraryKind = KotlinJavaScriptLibraryKind
    override fun getLibraryDescription(project: Project) = JSLibraryStdDescription(project)

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        if (!allowSlowOperations) return null

        return getGenericTestIcon(declaration, { declaration.resolveToDescriptorIfAny() }) {
            val contexts by lazy { computeConfigurationContexts(declaration) }

            val runConfigData = RunConfigurationProducer
                .getProducers(declaration.project)
                .asSequence()
                .filterIsInstance<KotlinJSRunConfigurationDataProvider<*>>()
                .filter { it.isForTests }
                .flatMap { provider -> contexts.map { context -> provider.getConfigurationData(context) } }
                .firstOrNull { it != null }
                ?: return@getGenericTestIcon null

            val location = if (runConfigData is KotlinJSRunConfigurationData) {
                FileUtil.toSystemDependentName(runConfigData.jsOutputFilePath)
            } else {
                declaration.containingKtFile.packageFqName.asString()
            }

            return@getGenericTestIcon SmartList(location)
        }
    }

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        val contexts by lazy { computeConfigurationContexts(function) }

        return RunConfigurationProducer
            .getProducers(function.project)
            .asSequence()
            .filterIsInstance<KotlinJSRunConfigurationDataProvider<*>>()
            .filter { !it.isForTests }
            .flatMap { provider -> contexts.map { context -> provider.getConfigurationData(context) } }
            .any { it != null }
    }

    private fun computeConfigurationContexts(declaration: KtNamedDeclaration): Sequence<ConfigurationContext> {
        val location = PsiLocation(declaration)
        return KotlinMultiplatformRunLocationsProvider().getAlternativeLocations(location).map {
            ConfigurationContext.createEmptyContextForLocation(it)
        }.ifEmpty { listOf(ConfigurationContext.createEmptyContextForLocation(location)) }.asSequence()
    }
}