// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.test.utils.withExtension
import org.junit.Assert
import java.io.File

fun KtCodeFragment.checkImports(testFile: File) {
    val importList = importsAsImportList()
    val importsText = StringUtil.convertLineSeparators(importList?.text ?: "")

    val importsAfterFile = testFile.withExtension(".kt.after.imports")
    if (importsAfterFile.exists()) {
        KotlinTestUtils.assertEqualsToFile(importsAfterFile, importsText)
    } else {
        Assert.assertTrue("Unexpected imports found: $importsText", importsText.isEmpty())
    }
}