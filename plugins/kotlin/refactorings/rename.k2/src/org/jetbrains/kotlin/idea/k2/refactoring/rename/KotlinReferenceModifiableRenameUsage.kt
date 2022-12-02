// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.refactoring.rename.api.PsiModifiableRenameUsage
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal class KotlinReferenceModifiableRenameUsage(
    private val referenceExpression: KtNameReferenceExpression
) : PsiModifiableRenameUsage {

    override fun createPointer(): Pointer<KotlinReferenceModifiableRenameUsage> {
        return Pointer.delegatingPointer(referenceExpression.createSmartPointer(), ::KotlinReferenceModifiableRenameUsage)
    }

    override val file: PsiFile
        get() = referenceExpression.containingFile

    override val range: TextRange
        get() = referenceExpression.textRange

    override val declaration: Boolean
        get() = false

    override val fileUpdater: ModifiableRenameUsage.FileUpdater
        get() = kotlinFileRangeUpdater()
}
