// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UVariable

class KotlinUVariable(
    psi: PsiVariable,
    override val sourcePsi: KtElement,
    givenParent: UElement?
) : AbstractKotlinUVariable(givenParent), UVariable, PsiVariable by psi {

    override val javaPsi = unwrap<UVariable, PsiVariable>(psi)

    override val psi = javaPsi

    override fun acceptsAnnotationTarget(target: AnnotationUseSiteTarget?): Boolean = true

    override fun getInitializer(): PsiExpression? {
        return super<AbstractKotlinUVariable>.getInitializer()
    }

    override fun getOriginalElement(): PsiElement? {
        return super<AbstractKotlinUVariable>.getOriginalElement()
    }

    override fun getNameIdentifier(): PsiIdentifier {
        return super.getNameIdentifier()
    }

    override fun getContainingFile(): PsiFile {
        return super.getContainingFile()
    }

}
