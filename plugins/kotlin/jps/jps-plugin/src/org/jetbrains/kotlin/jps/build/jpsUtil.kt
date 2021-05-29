// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.model.module.JpsModule

fun ModuleChunk.isDummy(context: CompileContext): Boolean {
    val targetIndex = context.projectDescriptor.buildTargetIndex
    return targets.all { targetIndex.isDummy(it) }
}

@Deprecated("Use `kotlin.targetBinding` instead", ReplaceWith("kotlin.targetsBinding"))
val CompileContext.kotlinBuildTargets
    get() = kotlin.targetsBinding

fun ModuleChunk.toKotlinChunk(context: CompileContext): KotlinChunk? =
    context.kotlin.getChunk(this)

fun ModuleBuildTarget(module: JpsModule, isTests: Boolean) =
    ModuleBuildTarget(
        module,
        if (isTests) JavaModuleBuildTargetType.TEST else JavaModuleBuildTargetType.PRODUCTION
    )

val JpsModule.productionBuildTarget
    get() = ModuleBuildTarget(this, false)

val JpsModule.testBuildTarget
    get() = ModuleBuildTarget(this, true)

