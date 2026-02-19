// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.j2k.AbstractJavaToKotlinCopyPasteConversionTest
import java.io.File

abstract class AbstractK1JavaToKotlinCopyPasteConversionTest : AbstractJavaToKotlinCopyPasteConversionTest() {
    override val testDataDirectory: File
        get() = KotlinRoot.DIR.resolve("j2k/shared/tests/testData/copyPaste")
}