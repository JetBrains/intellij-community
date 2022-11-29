// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.refactoring.rename.api.PsiModifiableRenameUsage
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

internal class KotlinNamedDeclarationRenameUsage private constructor(
    val element: KtNamedDeclaration
) : PsiModifiableRenameUsage, RenameTarget {

    override fun createPointer(): Pointer<KotlinNamedDeclarationRenameUsage> {
        return Pointer.delegatingPointer(element.createSmartPointer(), KotlinNamedDeclarationRenameUsage::create)
    }

    override val targetName: String
        get() = element.name ?: reportMissingName()

    override fun presentation(): TargetPresentation {
        return TargetPresentation.builder(targetName).presentation()
    }

    override val file: PsiFile
        get() = element.containingFile

    override val range: TextRange
        get() = element.nameIdentifier?.textRange ?: reportMissingName()

    override val declaration: Boolean
        get() = true

    override val fileUpdater: ModifiableRenameUsage.FileUpdater
        get() = kotlinFileRangeUpdater()

    private fun reportMissingName(): Nothing {
        throw KotlinExceptionWithAttachments("Rename cannot work on declaration without name")
            .withPsiAttachment("declaration", element)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KotlinNamedDeclarationRenameUsage

        if (element != other.element) return false

        return true
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }

    companion object {
        fun create(element: KtNamedDeclaration): KotlinNamedDeclarationRenameUsage? =
            if (element.nameIdentifier != null) KotlinNamedDeclarationRenameUsage(element) else null
    }
}
