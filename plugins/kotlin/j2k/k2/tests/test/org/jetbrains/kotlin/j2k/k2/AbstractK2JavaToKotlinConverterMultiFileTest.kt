// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2

import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.j2k.AbstractJavaToKotlinConverterMultiFileTest
import org.jetbrains.kotlin.j2k.getK2FileTextWithErrors
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractK2JavaToKotlinConverterMultiFileTest : AbstractJavaToKotlinConverterMultiFileTest() {
    override fun dumpTextWithErrors(kotlinFile: KtFile): String =
        getK2FileTextWithErrors(kotlinFile)

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}