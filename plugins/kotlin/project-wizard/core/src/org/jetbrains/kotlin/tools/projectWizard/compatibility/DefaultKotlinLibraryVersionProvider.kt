// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.projectStructure.ExternalCompilerVersionProvider
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider

class DefaultKotlinLibraryVersionProvider : KotlinLibraryVersionProvider {
    /**
     * Attempts to get the Kotlin compiler version used in this module
     * It attempts to find the Kotlin compiler version specified in the facet of this module.
     * If no facet is defined, the bundled standalone compiler version is used.
     */
    private fun Module.getKotlinVersion(): KotlinVersion? {
        return ExternalCompilerVersionProvider.get(this)?.kotlinVersion
            ?: KotlinJpsPluginSettings.jpsVersion(project).let { IdeKotlinVersion.opt(it) }?.kotlinVersion
    }


    override fun getVersion(module: Module, groupId: String, artifactId: String): String? {
        val kotlinVersion = module.getKotlinVersion() ?: return null

        val versions = KotlinLibrariesCompatibilityStore.getInstance().getVersions(groupId, artifactId).orEmpty()
        val kotlinShortVersion = "${kotlinVersion.major}.${kotlinVersion.minor}"
        return versions[kotlinShortVersion]
    }
}