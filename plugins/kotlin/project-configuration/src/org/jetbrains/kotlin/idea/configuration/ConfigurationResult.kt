// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.statistics.KotlinProjectConfigurationError

class ConfigurationResult(
    val configuredModules: Set<Module>,
    val changedFiles: ChangedConfiguratorFiles,
    val error: KotlinProjectConfigurationError?
)

class ConfigurationResultBuilder {

    val changedFiles: ChangedConfiguratorFiles = ChangedConfiguratorFiles()

    private val configuredModules = mutableSetOf<Module>()
    private var error: KotlinProjectConfigurationError? = null

    fun configuredModule(module: Module): ConfigurationResultBuilder {
        configuredModules.add(module)
        error = null
        return this
    }

    fun changedFile(file: PsiFile): ConfigurationResultBuilder {
        changedFiles.storeOriginalFileContent(file)
        return this
    }

    fun error(error: KotlinProjectConfigurationError): ConfigurationResultBuilder {
        this.error = error
        return this
    }

    fun build(): ConfigurationResult {
        return ConfigurationResult(configuredModules, changedFiles, error)
    }
}