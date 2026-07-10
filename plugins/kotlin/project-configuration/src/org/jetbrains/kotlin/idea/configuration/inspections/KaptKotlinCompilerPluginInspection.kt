// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.inspections

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.configuration.AbstractKotlinCompilerProjectPostConfigurator
import org.jetbrains.kotlin.idea.configuration.KotlinProjectPostConfigurator
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.psi.KtFile

class KaptKotlinCompilerPluginInspection : AbstractKotlinCompilerPluginInspection(KAPT_PLUGIN_ID) {
    override val descriptionTemplate: String
        get() = KotlinProjectConfigurationBundle.message("kapt.kotlin.compiler.plugin.inspection.problem.descriptor")

    override val familyName: String
        get() = KotlinProjectConfigurationBundle.message("kapt.kotlin.compiler.plugin.inspection.problem.quick.fix")

    override fun isAvailableForFileInModule(ktFile: KtFile, module: Module): Boolean =
        compilerPluginProjectConfigurators(module).isNotEmpty() &&
                KotlinProjectPostConfigurator.EP_NAME.findFirstSafe { postConfigurator ->
                    postConfigurator is AbstractKotlinCompilerProjectPostConfigurator &&
                            postConfigurator.isForCompilerPlugin(KAPT_PLUGIN_ID) &&
                            postConfigurator.isApplicable(module)
                } != null

    override fun isCompilerPluginRequired(file: KtFile): Boolean = true

    private companion object {
        private const val KAPT_PLUGIN_ID = "kapt"
    }
}
