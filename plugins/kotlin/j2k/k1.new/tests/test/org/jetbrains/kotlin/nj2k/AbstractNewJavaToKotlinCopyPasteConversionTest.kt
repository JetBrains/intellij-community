// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.conversion.copy.AbstractJavaToKotlinCopyPasteConversionTest
import java.io.File

abstract class AbstractNewJavaToKotlinCopyPasteConversionTest : AbstractJavaToKotlinCopyPasteConversionTest() {
    override val testDataDirectory: File
        get() = KotlinRoot.DIR.resolve("j2k/k1.new/tests/testData/copyPaste")

    override fun isNewJ2K(): Boolean = true
}