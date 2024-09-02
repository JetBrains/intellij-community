// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.intellij.openapi.roots.ExternalLibraryDescriptor
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider

class DefaultKotlinLibraryVersionProvider : KotlinLibraryVersionProvider {
    override fun getVersion(groupId: String, artifactId: String, projectKotlinVersion: KotlinVersion): ExternalLibraryDescriptor? {
        val versions = KotlinLibrariesCompatibilityStore.getInstance().getVersions(groupId, artifactId).orEmpty()
        val kotlinShortVersion = "${projectKotlinVersion.major}.${projectKotlinVersion.minor}"
        val versionToUse = versions[kotlinShortVersion] ?: return null

        return ExternalLibraryDescriptor(groupId, artifactId, versionToUse, versionToUse, versionToUse)
    }
}