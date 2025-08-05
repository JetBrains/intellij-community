// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiMember
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.*
import org.jetbrains.kotlin.nj2k.tree.Visibility.*

class InternalDeclarationConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKVisibilityOwner || element !is JKModalityOwner) return recurse(element)
        if (element.visibility != INTERNAL) return recurse(element)

        val containingClass: JKClass? = element.parentOfType<JKClass>()
        val psiContainingClass = element.psi<PsiMember>()?.containingClass
        val containingClassKind = containingClass?.classKind ?: psiContainingClass?.classKind?.toJk()

        val containingClassVisibility = containingClass?.visibility
            ?: psiContainingClass
                ?.visibility(context.converter.referenceSearcher, null)
                ?.visibility

        val defaultVisibility = when {
            context.converter.settings.publicByDefault -> PUBLIC

            containingClass == null && psiContainingClass != null -> {
                // indicates we are changing the context of the element in JKTree and should default to public
                PUBLIC
            }

            else -> INTERNAL
        }

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