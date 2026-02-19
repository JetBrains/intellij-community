// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.elements.KtLightIdentifier
import org.jetbrains.kotlin.psi.KtDeclaration

@ApiStatus.Internal
class UastLightIdentifier(
    lightOwner: PsiNameIdentifierOwner,
    ktDeclaration: KtDeclaration?
) : KtLightIdentifier(lightOwner, ktDeclaration) {
    override fun getContainingFile(): PsiFile = unwrapFakeFileForLightClass(super.getContainingFile())
}
