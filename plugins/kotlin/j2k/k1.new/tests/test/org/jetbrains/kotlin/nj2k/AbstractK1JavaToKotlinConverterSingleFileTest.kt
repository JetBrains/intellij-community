// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.j2k.AbstractJavaToKotlinConverterSingleFileTest
import org.jetbrains.kotlin.types.FlexibleTypeImpl

abstract class AbstractK1JavaToKotlinConverterSingleFileTest : AbstractJavaToKotlinConverterSingleFileTest() {
    override fun doTest(javaPath: String) {
        // TODO KTIJ-5630 (K1 only)
        FlexibleTypeImpl.RUN_SLOW_ASSERTIONS = !javaPath.endsWith("typeParameters/rawTypeCast.java")

        super.doTest(javaPath)
    }
}
