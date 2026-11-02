// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build.general

import org.jetbrains.kotlin.jps.build.KotlinBtaImplementation

/** The tests of [KotlinJpsBuildTest] with [KotlinBtaImplementation.STABLE]. */
class KotlinJpsBuildStableTest : KotlinJpsBuildTest() {
  override val btaImplementation: KotlinBtaImplementation
    get() = KotlinBtaImplementation.STABLE
}
