// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.targetApiImpl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.refactoring.safeDelete.api.PsiSafeDeleteDeclarationUsage
import com.intellij.refactoring.safeDelete.api.SafeDeleteTarget
import com.intellij.refactoring.safeDelete.impl.DefaultPsiSafeDeleteDeclarationUsage
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamed

class KotlinSafeDeleteTarget(val ktElement: KtElement) : SafeDeleteTarget {
    override fun createPointer(): Pointer<out SafeDeleteTarget> {
        return Pointer.delegatingPointer(ktElement.createSmartPointer(), ::KotlinSafeDeleteTarget)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is KotlinSafeDeleteTarget) return false
        return ktElement == other.ktElement
    }

    override fun hashCode(): Int {
        return ktElement.hashCode()
    }

    override fun declarations(): Collection<PsiSafeDeleteDeclarationUsage> {
        return listOf(DefaultPsiSafeDeleteDeclarationUsage(PsiUsage.textUsage(ktElement.containingFile, ktElement.textRange)))
    }

    override fun targetPresentation(): TargetPresentation {
        return TargetPresentation.builder((ktElement as? KtNamed)?.nameAsName?.asString() ?: "")
           // .icon(ktElement.getIcon(0))
            .presentation()
    }
}