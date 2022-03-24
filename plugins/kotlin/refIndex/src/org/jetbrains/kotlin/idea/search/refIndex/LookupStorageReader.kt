// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.project.Project
import com.intellij.util.io.*
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage.Companion.buildDataPaths
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage.Companion.kotlinDataContainer
import org.jetbrains.kotlin.incremental.storage.*
import org.jetbrains.kotlin.name.FqName
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

class LookupStorageReader private constructor(
    private val lookupStorage: PersistentHashMap<LookupSymbolKey, Collection<Int>>,
    private val idToFileStorage: PersistentHashMap<Int, String>,
    projectPath: String,
) {
    private val pathConverter = RelativeFileToPathConverter(File(projectPath))

    fun close() {
        lookupStorage.close()
        idToFileStorage.close()
    }

    operator fun get(fqName: FqName): List<Path> {
        val key = LookupSymbolKey(
            name = fqName.shortName().asString(),
            scope = fqName.parent().takeUnless(FqName::isRoot)?.asString() ?: "",
        )

        return lookupStorage[key]?.mapNotNull { idToFileStorage[it]?.let(pathConverter::toFile)?.toPath() }.orEmpty()
    }

    companion object {
        fun create(kotlinDataContainerPath: Path, projectPath: String): LookupStorageReader? {
            val lookupStoragePath = kotlinDataContainerPath.resolve(LOOKUP_STORAGE_NAME).takeIf { it.exists() } ?: return null
            val idToFileStoragePath = kotlinDataContainerPath.resolve(ID_TO_FILE_STORAGE_NAME).takeIf { it.exists() } ?: return null
            val storeFullFqNames = CompilerSystemProperties.COMPILE_INCREMENTAL_WITH_CLASSPATH_SNAPSHOTS.value.toBooleanLenient() ?: false
            val lookupStorage = openReadOnlyPersistentHashMap(
                lookupStoragePath,
                LookupSymbolKeyDescriptor(storeFullFqNames),
                IntCollectionExternalizer,
            )

            val idToFileStorage = openReadOnlyPersistentHashMap(
                idToFileStoragePath,
                ExternalIntegerKeyDescriptor.INSTANCE,
                EnumeratorStringDescriptor.INSTANCE,
            )

            return LookupStorageReader(lookupStorage, idToFileStorage, projectPath)
        }

        fun hasStorage(project: Project): Boolean = project.buildDataPaths
            ?.kotlinDataContainer
            ?.resolve(LOOKUP_STORAGE_NAME)
            ?.exists() == true

        private val LOOKUP_STORAGE_NAME = "lookups.${BasicMapsOwner.CACHE_EXTENSION}"
        private val ID_TO_FILE_STORAGE_NAME = "id-to-file.${BasicMapsOwner.CACHE_EXTENSION}"
    }
}

internal fun <K, D> openReadOnlyPersistentHashMap(
    storagePath: Path,
    keyDescriptor: KeyDescriptor<K>,
    dataExternalizer: DataExternalizer<D>,
): PersistentHashMap<K, D> = PersistentMapBuilder.newBuilder(storagePath, keyDescriptor, dataExternalizer).readonly().build()
