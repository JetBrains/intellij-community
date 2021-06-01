// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.storage.BuildDataManager
import org.jetbrains.kotlin.incremental.KOTLIN_CACHE_DIRECTORY_NAME
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget
import java.io.File

private val HAS_KOTLIN_MARKER_FILE_NAME = "has-kotlin-marker.txt"
private val REBUILD_AFTER_CACHE_VERSION_CHANGE_MARKER = "rebuild-after-cache-version-change-marker.txt"

abstract class MarkerFile(private val fileName: String, private val paths: BuildDataPaths) {
    operator fun get(target: KotlinModuleBuildTarget<*>): Boolean? =
        get(target.jpsModuleBuildTarget)

    operator fun get(target: ModuleBuildTarget): Boolean? {
        val file = target.markerFile

        if (!file.exists()) return null

        return file.readText().toBoolean()
    }

    operator fun set(target: KotlinModuleBuildTarget<*>, value: Boolean) =
        set(target.jpsModuleBuildTarget, value)

    operator fun set(target: ModuleBuildTarget, value: Boolean) {
        val file = target.markerFile

        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        file.writeText(value.toString())
    }

    fun clean(target: KotlinModuleBuildTarget<*>) =
        clean(target.jpsModuleBuildTarget)

    fun clean(target: ModuleBuildTarget) {
        target.markerFile.delete()
    }

    private val ModuleBuildTarget.markerFile: File
        get() {
            val directory = File(paths.getTargetDataRoot(this), KOTLIN_CACHE_DIRECTORY_NAME)
            return File(directory, fileName)
        }
}

class HasKotlinMarker(dataManager: BuildDataManager) : MarkerFile(HAS_KOTLIN_MARKER_FILE_NAME, dataManager.dataPaths)
class RebuildAfterCacheVersionChangeMarker(dataManager: BuildDataManager) :
    MarkerFile(REBUILD_AFTER_CACHE_VERSION_CHANGE_MARKER, dataManager.dataPaths)
