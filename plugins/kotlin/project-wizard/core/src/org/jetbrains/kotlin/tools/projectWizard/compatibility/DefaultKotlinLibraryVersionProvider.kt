// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.config.toKotlinVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider

class DefaultKotlinLibraryVersionProvider : KotlinLibraryVersionProvider {
    override fun getVersion(module: Module, groupId: String, artifactId: String): String? {
        val projectKotlinVersion = module.languageVersionSettings.languageVersion.toKotlinVersion()

        val versions = KotlinLibrariesCompatibilityStore.getInstance().getVersions(groupId, artifactId).orEmpty()
        val kotlinShortVersion = "${projectKotlinVersion.major}.${projectKotlinVersion.minor}"
        return versions[kotlinShortVersion]
    }
}