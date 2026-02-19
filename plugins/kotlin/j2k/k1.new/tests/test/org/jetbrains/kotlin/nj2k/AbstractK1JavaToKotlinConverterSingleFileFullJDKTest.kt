// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.idea.test.dumpTextWithErrors
import org.jetbrains.kotlin.j2k.AbstractJavaToKotlinConverterSingleFileFullJDKTest
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK1JavaToKotlinConverterSingleFileFullJDKTest : AbstractJavaToKotlinConverterSingleFileFullJDKTest() {
    override fun dumpTextWithErrors(createKotlinFile: KtFile): String {
        return createKotlinFile.dumpTextWithErrors()
    }
}