// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.PsiAnnotationParameterList
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameValuePair
import org.jetbrains.kotlin.asJava.elements.KtLightElementBase
import org.jetbrains.kotlin.psi.KtElement

class KtLightEmptyAnnotationParameterList(parent: PsiElement) : KtLightElementBase(parent), PsiAnnotationParameterList {
    override val kotlinOrigin: KtElement? get() = null
    override fun getAttributes(): Array<PsiNameValuePair> = emptyArray()
}
