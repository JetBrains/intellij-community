// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiMember
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.*
import org.jetbrains.kotlin.nj2k.tree.Visibility.*

class InternalDeclarationConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKVisibilityOwner || element !is JKModalityOwner) return recurse(element)
        if (element.visibility != INTERNAL) return recurse(element)

        val containingClass = element.parentOfType<JKClass>()
        val containingClassKind = containingClass?.classKind ?: element.psi<PsiMember>()?.containingClass?.classKind?.toJk()

        val containingClassVisibility = containingClass?.visibility
            ?: element.psi<PsiMember>()
                ?.containingClass
                ?.visibility(context.converter.referenceSearcher, null)
                ?.visibility
        val defaultVisibility = if (context.converter.settings.publicByDefault) PUBLIC else INTERNAL

        element.visibility = when {
            containingClassKind == INTERFACE || containingClassKind == ANNOTATION ->
                PUBLIC

            containingClassKind == ENUM && element is JKConstructor ->
                PRIVATE

            element is JKClass && element.isLocalClass() ->
                PUBLIC

            element is JKConstructor && containingClassVisibility != INTERNAL ->
                defaultVisibility

            element is JKField || element is JKMethod ->
                PUBLIC

            else -> defaultVisibility
        }

        return recurse(element)
    }
}