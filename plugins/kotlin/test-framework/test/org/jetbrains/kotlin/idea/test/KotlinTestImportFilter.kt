// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.codeInsight.ImportFilter
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils

object KotlinTestImportFilter : ImportFilter() {
    override fun shouldUseFullyQualifiedName(targetFile: PsiFile, classQualifiedName: String): Boolean {
        if (!isUnitTestMode()) {
            return false
        }
        val doNotImport = InTextDirectivesUtils.findLinesWithPrefixesRemoved(targetFile.text, "// DO_NOT_IMPORT:")
        return classQualifiedName in doNotImport
    }
}