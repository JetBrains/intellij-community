// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.getKotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModule
import org.jetbrains.kotlin.idea.configuration.inspections.AbstractKotlinCompilerPluginInspection.Companion.hasCompilerPluginExtension
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle

@ApiStatus.Internal
abstract class AbstractKotlinCompilerProjectPostConfigurator(protected val kotlinCompilerPluginId: String): KotlinProjectPostConfigurator {
    override val name: String
        get() = KotlinProjectConfigurationBundle.message("kotlin.compiler.plugin.0", kotlinCompilerPluginId)

    protected fun compilerPluginProjectConfigurators(): List<KotlinCompilerPluginProjectConfigurator> =
        KotlinCompilerPluginProjectConfigurator.compilerPluginProjectConfigurators(kotlinCompilerPluginId)

    protected fun Module.hasCompilerPluginExtension(filter: (FirExtensionRegistrarAdapter) -> Boolean): Boolean {
        val kotlinSourceRootType = getKotlinSourceRootType() ?: return false
        val kind = when (kotlinSourceRootType) {
            SourceKotlinRootType -> KaSourceModuleKind.PRODUCTION
            TestSourceKotlinRootType -> KaSourceModuleKind.TEST
        }
        val module = toKaSourceModule(kind) ?: return false
        return module.hasCompilerPluginExtension(filter)
    }

    override fun configureModule(module: Module, configurationResultBuilder: ConfigurationResultBuilder) {
        val configurators =
            compilerPluginProjectConfigurators().ifEmpty { return }

        for (configurator in configurators) {
            configurator.configureModule(module, configurationResultBuilder)
        }
    }
}
