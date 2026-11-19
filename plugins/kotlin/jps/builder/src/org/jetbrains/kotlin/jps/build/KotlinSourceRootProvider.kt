/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.io.FileFilters
import org.jetbrains.jps.builders.AdditionalRootsProviderService
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.config.KotlinResourceRootType
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestResourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.jps.model.expectedByModules
import org.jetbrains.kotlin.jps.model.isTestModule
import org.jetbrains.kotlin.jps.model.sourceSetModules
import java.io.File

class KotlinSourceRootProvider : AdditionalRootsProviderService<JavaSourceRootDescriptor>(JavaModuleBuildTargetType.ALL_TYPES) {
    override fun getAdditionalRoots(
        target: BuildTarget<JavaSourceRootDescriptor>,
        dataPaths: BuildDataPaths,
    ): List<JavaSourceRootDescriptor> {
        val moduleBuildTarget = target as? ModuleBuildTarget ?: return listOf()
        val module = moduleBuildTarget.module

        val result = mutableListOf<JavaSourceRootDescriptor>()

        // Add source roots with type KotlinSourceRootType.
        //
        // Note: `KotlinSourceRootType` cannot be supported directly, since `SourceRootDescriptors` are computed by
        // `ModuleBuildTarget.computeAllTargets`. `ModuleBuildTarget` is required for incremental compilation.
        // We cannot define our own `ModuleBuildTarget` since it is final and `ModuleBuildTarget` supports only `JavaSourceRootDescriptor`.
        // So the only one way to support `KotlinSourceRootType` is to add a fake `JavaSourceRootDescriptor` for each source root with that type.
        val kotlinSourceRootType = if (target.isTests) TestSourceKotlinRootType else SourceKotlinRootType
        module.getSourceRoots(kotlinSourceRootType).forEach {
            result.add(
                JavaSourceRootDescriptor.createJavaSourceRootDescriptor(
                    it.file,
                    target,
                    false,
                    false,
                    it.properties.packagePrefix,
                    setOf(),
                    FileFilters.EVERYTHING,
                )
            )
        }

        for (includedSourceRoot in moduleBuildTarget.includedModuleSourceRoots()) {
            val javaSourceRootProperties = includedSourceRoot.properties as? JavaSourceRootProperties
            result.add(
                JavaSourceRootDescriptor.createJavaSourceRootDescriptor(
                    includedSourceRoot.file,
                    target,
                    javaSourceRootProperties?.isForGeneratedSources ?: false,
                    false,
                    javaSourceRootProperties?.packagePrefix ?: "",
                    setOf(),
                    FileFilters.EVERYTHING,
                )
            )
        }

        return result
    }
}

/**
 * Returns the source roots of the other modules that the Kotlin builder compiles as a part of this target.
 * These are the roots of the source set modules and of the `expectedBy` modules.
 */
internal fun ModuleBuildTarget.includedModuleSourceRoots(): List<JpsModuleSourceRoot> {
    val includedModules = buildList {
        // new multiplatform model support:
        if (isTests == module.isTestModule) {
            addAll(module.sourceSetModules)
        }
        // legacy multiplatform model support:
        addAll(module.expectedByModules)
    }
    return includedModules.flatMap { includedModule ->
        includedModule.sourceRoots.filter { it.rootType.isTestsRootType == isTests }
    }
}

/**
 * Returns the root directories of [includedModuleSourceRoots]. The sources under these directories are cross compiled.
 */
internal fun ModuleBuildTarget.includedModuleSourceRootFiles(): Set<File> =
    includedModuleSourceRoots().mapTo(HashSet()) { it.file }

private val JpsModuleSourceRootType<*>.isTestsRootType
    get() = when (this) {
        is KotlinSourceRootType -> this == TestSourceKotlinRootType
        is KotlinResourceRootType -> this == TestResourceKotlinRootType
        // for compatibility:
        is JavaSourceRootType -> this == JavaSourceRootType.TEST_SOURCE
        is JavaResourceRootType -> this == JavaResourceRootType.TEST_RESOURCE
        else -> null
    }