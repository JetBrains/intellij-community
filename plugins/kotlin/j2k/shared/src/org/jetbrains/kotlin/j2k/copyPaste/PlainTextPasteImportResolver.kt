// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.psi.PsiImportStatementBase

/**
 * This class tries to prepare the plain text Java code for J2K by adding necessary imports so that
 * such code can be properly resolved and converted.
 *
 * 1. For every Kotlin file import statement, try to convert it to a Java PSI import and add it to the Java file.
 * 2. For every unresolved short reference in the dummy Java file:
 *      * Try to find a visible class, method, or field with the same short name
 *      * If such a declaration is found, add an import for it (usually to both the Java and Kotlin files).
 *      Note: imports for Java are added at once, but for Kotlin they are returned as a list, to be converted by J2K later.
 */
interface PlainTextPasteImportResolver {
    fun generateRequiredImports(): List<PsiImportStatementBase>
}
