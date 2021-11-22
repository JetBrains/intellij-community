// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.incremental

import org.jetbrains.jps.builders.*
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.indices.IgnoredFileIndex
import org.jetbrains.jps.indices.ModuleExcludeIndex
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.kotlin.config.SettingConstants
import java.io.File

object KotlinDataContainerTargetType : BuildTargetType<KotlinDataContainerTarget>(SettingConstants.KOTLIN_DATA_CONTAINER_ID) {
    override fun computeAllTargets(model: JpsModel): List<KotlinDataContainerTarget> = listOf(KotlinDataContainerTarget)

    override fun createLoader(model: JpsModel): BuildTargetLoader<KotlinDataContainerTarget> =
        object : BuildTargetLoader<KotlinDataContainerTarget>() {
            override fun createTarget(targetId: String): KotlinDataContainerTarget = KotlinDataContainerTarget
        }
}

// Fake target to store data per project for incremental compilation
object KotlinDataContainerTarget : BuildTarget<BuildRootDescriptor>(KotlinDataContainerTargetType) {
    override fun getId(): String = SettingConstants.KOTLIN_DATA_CONTAINER_ID
    override fun getPresentableName(): String = SettingConstants.KOTLIN_DATA_CONTAINER_ID

    override fun computeRootDescriptors(
        model: JpsModel?,
        index: ModuleExcludeIndex?,
        ignoredFileIndex: IgnoredFileIndex?,
        dataPaths: BuildDataPaths?
    ): List<BuildRootDescriptor> = listOf()

    override fun getOutputRoots(context: CompileContext): Collection<File> {
        val dataManager = context.projectDescriptor.dataManager
        val storageRoot = dataManager.dataPaths.dataStorageRoot
        return listOf(File(storageRoot, SettingConstants.KOTLIN_DATA_CONTAINER_ID))
    }

    override fun findRootDescriptor(rootId: String?, rootIndex: BuildRootIndex?): BuildRootDescriptor? = null

    override fun computeDependencies(
        targetRegistry: BuildTargetRegistry?,
        outputIndex: TargetOutputIndex?
    ): Collection<BuildTarget<*>> = listOf()
}
