// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface KotlinLibraryVersionProvider {
    companion object {
        val EP_NAME: ExtensionPointName<KotlinLibraryVersionProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.libraryVersionProvider")
    }

    /**
     * Returns a version of the library identified by the [groupId] and [artifactId] compatible with the [projectKotlinVersion].
     * Returns null if the [KotlinLibraryVersionProvider] does not manage the given library or no compatible version could be found.
     */
    fun getVersion(module: Module, groupId: String, artifactId: String): String?
}