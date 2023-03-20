// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.searching.usages

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.NonCodeSearchDescriptionLocation
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class KotlinNonCodeSearchElementDescriptionProvider : ElementDescriptionProvider {
    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        if (location !is NonCodeSearchDescriptionLocation) return null
        val declaration = element.namedUnwrappedElement as? KtNamedDeclaration ?: return null
        return if (location.isNonJava) (declaration.fqName?.asString() ?: declaration.name) else declaration.name
    }
}
