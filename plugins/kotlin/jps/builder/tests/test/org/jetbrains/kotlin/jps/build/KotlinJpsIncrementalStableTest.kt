// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

/** The cases of [KotlinJpsIncrementalTestGenerated] with [KotlinBtaImplementation.STABLE]. */
class KotlinJpsIncrementalStableTest : KotlinJpsIncrementalTestGenerated() {
  override val btaImplementation: KotlinBtaImplementation
    get() = KotlinBtaImplementation.STABLE
}
