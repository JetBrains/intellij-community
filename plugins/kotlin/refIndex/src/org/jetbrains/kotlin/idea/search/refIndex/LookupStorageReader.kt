// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage.Companion.buildDataPaths
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage.Companion.kotlinDataContainer
import org.jetbrains.kotlin.incremental.LookupStorage
import org.jetbrains.kotlin.incremental.LookupSymbol
import org.jetbrains.kotlin.incremental.storage.BasicMapsOwner
import org.jetbrains.kotlin.incremental.storage.RelativeFileToPathConverter
import java.io.File
import java.nio.file.Path

class LookupStorageReader private constructor(private val lookupStorage: LookupStorage) {
    fun close(): Unit = lookupStorage.close()
    fun get(lookupSymbol: LookupSymbol): Collection<String> = lookupStorage.get(lookupSymbol)

    companion object {
        fun create(kotlinDataContainerPath: Path, projectPath: String): LookupStorageReader? = LookupStorageReader(
            LookupStorage(kotlinDataContainerPath.toFile(), RelativeFileToPathConverter(File(projectPath)))
        )

        fun hasStorage(project: Project): Boolean = project.buildDataPaths
            ?.kotlinDataContainer
            ?.resolve(LOOKUP_STORAGE_NAME)
            ?.exists() == true

        private val LOOKUP_STORAGE_NAME = "lookups.${BasicMapsOwner.CACHE_EXTENSION}"
    }
}