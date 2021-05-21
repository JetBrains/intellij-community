// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.asJava.elements.KtLightIdentifier
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.kotlin.createKDocNameSimpleNameReference
import org.jetbrains.uast.toUElement

class UastLightIdentifier(
    lightOwner: PsiNameIdentifierOwner,
    ktDeclaration: KtDeclaration?
) : KtLightIdentifier(lightOwner, ktDeclaration) {
    override fun getContainingFile(): PsiFile {
        return unwrapFakeFileForLightClass(super.getContainingFile())
    }
}
