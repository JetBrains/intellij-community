// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.elements.KtLightIdentifierWithOrigin
import org.jetbrains.kotlin.psi.*

@ApiStatus.Internal
class UastLightIdentifier(
    lightOwner: PsiNameIdentifierOwner,
    ktDeclaration: KtDeclaration?
) : KtLightIdentifierWithOrigin(lightOwner, ktDeclaration) {
    override fun getContainingFile(): PsiFile {
        return unwrapFakeFileForLightClass(super.getContainingFile())
    }
}
