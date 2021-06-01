// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget

interface KotlinJpsCompilerArgumentsProvider {
    fun getExtraArguments(moduleBuildTarget: ModuleBuildTarget, context: CompileContext): List<String>
    fun getClasspath(moduleBuildTarget: ModuleBuildTarget, context: CompileContext): List<String>
}