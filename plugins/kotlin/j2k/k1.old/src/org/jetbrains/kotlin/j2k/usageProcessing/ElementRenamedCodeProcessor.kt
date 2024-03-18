// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.usageProcessing

import com.intellij.psi.PsiReference

class ElementRenamedCodeProcessor(private val newName: String) : ExternalCodeProcessor {
    override fun processUsage(reference: PsiReference): Array<PsiReference> {
        return reference.handleElementRename(newName).references
    }
}