// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.incremental

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException
import org.jetbrains.jps.builders.storage.StorageProvider
import org.jetbrains.jps.incremental.storage.BuildDataManager
import org.jetbrains.jps.incremental.storage.StorageOwner
import org.jetbrains.kotlin.incremental.LookupStorage
import org.jetbrains.kotlin.incremental.storage.FileToPathConverter
import java.io.File
import java.io.IOException

private object LookupStorageLock

class JpsLookupStorageManager(
    private val buildDataManager: BuildDataManager,
    pathConverter: FileToPathConverter
) {
    private val storageProvider = JpsLookupStorageProvider(pathConverter)

    fun cleanLookupStorage(log: Logger) {
        synchronized(LookupStorageLock) {
            try {
                buildDataManager.cleanTargetStorages(KotlinDataContainerTarget)
            } catch (e: IOException) {
                if (!buildDataManager.dataPaths.getTargetDataRoot(KotlinDataContainerTarget).deleteRecursively()) {
                    log.debug("Could not clear lookup storage caches", e)
                }
            }
        }
    }

    fun <T> withLookupStorage(fn: (LookupStorage) -> T): T {
        synchronized(LookupStorageLock) {
            try {
                val lookupStorage = buildDataManager.getStorage(KotlinDataContainerTarget, storageProvider)
                return fn(lookupStorage)
            } catch (e: IOException) {
                throw BuildDataCorruptedException(e)
            }
        }
    }

    private class JpsLookupStorageProvider(
        private val pathConverter: FileToPathConverter
    ) : StorageProvider<JpsLookupStorage>() {
        override fun createStorage(targetDataDir: File): JpsLookupStorage =
            JpsLookupStorage(targetDataDir, pathConverter)
    }

    private class JpsLookupStorage(
        targetDataDir: File,
        pathConverter: FileToPathConverter
    ) : StorageOwner, LookupStorage(targetDataDir, pathConverter)
}
