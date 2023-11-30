// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.base.injection.KotlinStdlibInjectionTestBase
import org.jetbrains.kotlin.idea.fir.invalidateCaches

class K2KotlinStdlibInjectionTest: KotlinStdlibInjectionTestBase() {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }
}