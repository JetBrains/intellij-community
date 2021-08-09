// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.generateRecursiveSequence
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.PersistentHashMap
import com.intellij.util.io.PersistentMapBuilder
import com.intellij.util.io.externalizer.StringCollectionExternalizer
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.kotlin.incremental.KOTLIN_CACHE_DIRECTORY_NAME
import org.jetbrains.kotlin.incremental.LookupStorage
import org.jetbrains.kotlin.incremental.storage.CollectionExternalizer
import org.jetbrains.kotlin.incremental.storage.FileToPathConverter
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.nio.file.Path
import kotlin.io.path.*

class KotlinCompilerReferenceIndexStorage(
    private val targetDataDir: Path,
    pathConverter: FileToPathConverter,
) : LookupStorage(
    targetDataDir.toFile(),
    pathConverter,
) {
    companion object {
        /**
         * [org.jetbrains.kotlin.incremental.AbstractIncrementalCache.Companion.SUBTYPES]
         */
        private const val SUBTYPES = "subtypes"
    }

    /**
     * [org.jetbrains.kotlin.incremental.storage.BasicMapsOwner.storageFile]
     */
    private val String.asStorageName: String get() = "$this.$CACHE_EXTENSION"
    private val String.storagePath: Path get() = targetDataDir.resolve(asStorageName)

    private val subtypesStorage = ClassOneToManyStorage(SUBTYPES.storagePath)

    override fun close() {
        subtypesStorage.closeAndClean()
        super.close()
    }

    fun getSubtypesOf(fqName: FqName, deep: Boolean): Sequence<FqName> = subtypesStorage[fqName, deep]

    fun initialize(buildDataPaths: BuildDataPaths) {
        for (buildTargetType in JavaModuleBuildTargetType.ALL_TYPES) {
            val buildTargetPath = buildDataPaths.getTargetTypeDataRoot(buildTargetType).toPath()
            if (buildTargetPath.notExists() || !buildTargetPath.isDirectory()) continue
            buildTargetPath.forEachDirectoryEntry { targetDataRoot ->
                val workingPath = targetDataRoot.takeIf { it.isDirectory() }
                    ?.resolve(KOTLIN_CACHE_DIRECTORY_NAME)
                    ?.takeUnless { it.notExists() }
                    ?: return@forEachDirectoryEntry

                initializeStorage(workingPath.resolve(SUBTYPES.asStorageName))
            }
        }
    }

    private fun initializeStorage(subtypesSourcePath: Path) {
        if (subtypesSourcePath.notExists()) return

        createKotlinDataReader(subtypesSourcePath).use { source ->
            source.processKeysWithExistingMapping { key ->
                source[key]?.let { values ->
                    subtypesStorage.add(key, values)
                }

                true
            }
        }
    }
}

private fun createKotlinDataReader(storagePath: Path): PersistentHashMap<String, Collection<String>> = PersistentMapBuilder.newBuilder(
    storagePath,
    EnumeratorStringDescriptor.INSTANCE,
    CollectionExternalizer<String>(EnumeratorStringDescriptor.INSTANCE, ::ArrayList),
).withReadonly(true).build()

private class ClassOneToManyStorage(storagePath: Path) {
    init {
        val storageName = storagePath.name
        storagePath.parent.listDirectoryEntries("$storageName*").ifNotEmpty {
            forEach { it.deleteIfExists() }
            LOG.warn("'$storageName' was deleted")
        }
    }

    private val storage = PersistentMapBuilder.newBuilder(
        storagePath,
        EnumeratorStringDescriptor.INSTANCE,
        externalizer,
    ).build()

    fun closeAndClean(): Unit = storage.closeAndClean()

    fun add(key: String, newValues: Collection<String>) {
        newValues.singleOrNull()?.let { newValue ->
            return add(key, newValue)
        }

        storage.put(key, storage[key]?.toMutableSet()?.apply { this += newValues } ?: newValues)
    }

    fun add(key: String, newValue: String) {
        val oldValues = storage[key]
        if (oldValues == null) {
            storage.put(key, listOf(newValue))
            return
        }

        if (newValue in oldValues) return

        storage.put(key, oldValues + newValue)
    }

    operator fun get(key: FqName, deep: Boolean): Sequence<FqName> = get(key.asString(), deep).map(::FqName)
    operator fun get(key: String, deep: Boolean): Sequence<String> {
        val values = storage[key]?.asSequence() ?: emptySequence()
        if (!deep) return values

        return generateRecursiveSequence(values) {
            storage[it]?.asSequence() ?: emptySequence()
        }
    }

    companion object {
        private val externalizer = StringCollectionExternalizer<Collection<String>>(::ArrayList)
        private val LOG = logger<ClassOneToManyStorage>()
    }
}
