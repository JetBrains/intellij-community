// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.idea.conversion.copy.AbstractTextJavaToKotlinCopyPasteConversionTest

abstract class AbstractTextNewJavaToKotlinCopyPasteConversionTest : AbstractTextJavaToKotlinCopyPasteConversionTest() {
    override fun isNewJ2K(): Boolean = true
}