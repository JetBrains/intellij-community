// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility.libraries

import com.intellij.openapi.roots.ExternalLibraryDescriptor
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataStorage

class DefaultKotlinLibraryVersionProvider : KotlinLibraryVersionProvider {
    private val libraryStorages = mutableListOf<IdeVersionedDataStorage<KotlinLibraryCompatibilityState>>()

    init {
        libraryStorages.add(CoroutinesLibraryCompatibilityStore.getInstance())
    }

    override fun getVersion(groupId: String, artifactId: String, projectKotlinVersion: KotlinVersion): ExternalLibraryDescriptor? {
        for (storage in libraryStorages) {
            val currentState = storage.state ?: continue
            if (currentState.groupId != groupId || currentState.artifactId != artifactId) continue
            val versions = currentState.versions
            val kotlinShortVersion = "${projectKotlinVersion.major}.${projectKotlinVersion.minor}"
            val versionToUse = versions[kotlinShortVersion] ?: continue

            return ExternalLibraryDescriptor(groupId, artifactId, versionToUse, versionToUse, versionToUse)
        }
        return null
    }
}