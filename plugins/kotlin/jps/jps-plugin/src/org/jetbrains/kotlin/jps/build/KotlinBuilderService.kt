// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.incremental.BuilderService
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.kotlin.jps.incremental.KotlinCompilerReferenceIndexBuilder

class KotlinBuilderService : BuilderService() {
    override fun createModuleLevelBuilders(): List<ModuleLevelBuilder> = listOf(KotlinBuilder(), KotlinCompilerReferenceIndexBuilder())
}
