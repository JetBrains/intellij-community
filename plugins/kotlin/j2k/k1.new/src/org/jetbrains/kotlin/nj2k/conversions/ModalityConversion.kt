// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.*
import org.jetbrains.kotlin.nj2k.tree.Modality.*
import org.jetbrains.kotlin.nj2k.tree.Visibility.PRIVATE

class ModalityConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        when (element) {
            is JKClass -> element.process()
            is JKMethod -> element.process()
            is JKField -> element.process()
        }
        return recurse(element)
    }

    private fun JKClass.process() {
        modality = when {
            classKind == ENUM || classKind == RECORD -> FINAL
            classKind == INTERFACE -> OPEN
            modality == OPEN && context.converter.settings.openByDefault -> OPEN
            modality == OPEN && !hasInheritors(psi as PsiClass) -> FINAL
            else -> modality
        }
    }

    private fun hasInheritors(psiClass: PsiClass): Boolean =
        context.converter.referenceSearcher.hasInheritors(psiClass)

    private fun JKMethod.process() {
        val psiMethod = psi<PsiMethod>() ?: return
        val containingClass: JKClass? = parentOfType<JKClass>()
        when {
            visibility == PRIVATE -> modality = FINAL

            modality != ABSTRACT && psiMethod.findSuperMethods().isNotEmpty() -> {
                modality = FINAL
                if (!hasOtherModifier(OtherModifier.OVERRIDE)) {
                    otherModifierElements += JKOtherModifierElement(OtherModifier.OVERRIDE)
                }
            }

            modality == OPEN
                    && context.converter.settings.openByDefault
                    && containingClass?.modality == OPEN
                    && visibility != PRIVATE -> {
                // do nothing, i.e. preserve the open modality
            }

            modality == OPEN
                    && containingClass?.classKind != INTERFACE
                    && !hasOverrides(psiMethod) -> {
                modality = FINAL
            }

            else -> {
                // do nothing
            }
        }
    }

    private fun hasOverrides(psiMethod: PsiMethod): Boolean =
        context.converter.referenceSearcher.hasOverrides(psiMethod)

    private fun JKField.process() {
        val containingClass = parentOfType<JKClass>() ?: return
        if (containingClass.classKind == INTERFACE) modality = FINAL
    }
}