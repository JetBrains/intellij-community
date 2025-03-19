// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2

import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.j2k.AbstractJavaToKotlinConverterPartialTest

abstract class AbstractK2JavaToKotlinConverterPartialTest : AbstractJavaToKotlinConverterPartialTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}