// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.j2k.AbstractJavaToKotlinConverterSingleFileFullJDKTest

abstract class AbstractK2JavaToKotlinConverterSingleFileFullJDKTest : AbstractJavaToKotlinConverterSingleFileFullJDKTest() {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }
}