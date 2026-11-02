// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.incremental.BuilderService
import org.jetbrains.jps.incremental.ModuleLevelBuilder

/** Registers the Kotlin builders in the build process. */
class KotlinBuilderService : BuilderService() {
  override fun createModuleLevelBuilders(): List<ModuleLevelBuilder> = listOf(KotlinBuilder())
}
