// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility.libraries

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataStorage

@State(name = "CoroutinesLibraryCompatibilityStore", storages = [Storage("kotlin-wizard-data.xml")])
class CoroutinesLibraryCompatibilityStore : IdeVersionedDataStorage<KotlinLibraryCompatibilityState>(
    parser = KotlinLibraryCompatibilityParser,
    defaultState = COROUTINES_LIBRARY_COMPATIBILITY_DEFAULT_DATA
) {
    override fun newState(): KotlinLibraryCompatibilityState = KotlinLibraryCompatibilityState()

    companion object {
        @JvmStatic
        fun getInstance(): CoroutinesLibraryCompatibilityStore {
            return service()
        }
    }
}