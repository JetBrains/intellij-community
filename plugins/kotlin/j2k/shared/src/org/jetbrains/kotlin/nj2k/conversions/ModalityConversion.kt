// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.annotationByFqName
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.*
import org.jetbrains.kotlin.nj2k.tree.Modality.*
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.OVERRIDE
import org.jetbrains.kotlin.nj2k.tree.Visibility.PRIVATE

/**
 * Updates the modality (open, final, abstract) of some declarations.
 * Also, adds the "override" modifier to applicable methods.
 */
class ModalityConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
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
        addOverrideModifierIfNeeded()
        updateModality()
    }

    private fun JKMethod.addOverrideModifierIfNeeded() {
        val psiMethod = psi<PsiMethod>() ?: return
        val overrideAnnotation = annotationList.annotationByFqName("java.lang.Override")

        if (!hasOtherModifier(OVERRIDE) && (overrideAnnotation != null || psiMethod.findSuperMethods().isNotEmpty())) {
            otherModifierElements += JKOtherModifierElement(OVERRIDE)
            if (overrideAnnotation != null) {
                annotationList.annotations -= overrideAnnotation
            }
        }
    }

    private fun JKMethod.updateModality() {
        val psiMethod = psi<PsiMethod>() ?: return
        val containingClass: JKClass? = parentOfType<JKClass>()
        when {
            visibility == PRIVATE -> modality = FINAL

            modality != ABSTRACT && hasOtherModifier(OVERRIDE) -> {
                modality = FINAL
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