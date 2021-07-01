// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.ULocalVariableEx
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.visitor.UastVisitor

open class KotlinULocalVariable(
    psi: PsiLocalVariable,
    override val sourcePsi: KtElement?,
    givenParent: UElement?
) : AbstractKotlinUVariable(givenParent), ULocalVariableEx, PsiLocalVariable by psi {

    override val javaPsi = unwrap<ULocalVariable, PsiLocalVariable>(psi)

    override fun acceptsAnnotationTarget(target: AnnotationUseSiteTarget?): Boolean = true

    override val psi = javaPsi

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

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitLocalVariable(this)) return
        uAnnotations.acceptList(visitor)
        uastInitializer?.accept(visitor)
        delegateExpression?.accept(visitor)
        visitor.afterVisitLocalVariable(this)
    }
}
