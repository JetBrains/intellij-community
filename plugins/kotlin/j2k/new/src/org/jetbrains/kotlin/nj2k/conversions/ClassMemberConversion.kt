// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.j2k.ast.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKFieldDataFromJava
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKMethodData
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.throwsAnnotation
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.Modality.FINAL
import org.jetbrains.kotlin.nj2k.tree.Mutability.IMMUTABLE
import org.jetbrains.kotlin.nj2k.tree.Mutability.MUTABLE
import org.jetbrains.kotlin.nj2k.types.updateNullabilityRecursively

class ClassMemberConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        when (element) {
            is JKField -> convertField(element)
            is JKMethodImpl -> convertMethod(element)
        }
        return recurse(element)
    }

    private fun convertMethod(method: JKMethodImpl) {
        with(method) {
            if (throwsList.isNotEmpty()) {
                annotationList.annotations +=
                    throwsAnnotation(throwsList.map { it.type.updateNullabilityRecursively(NotNull) }, symbolProvider)
            }

            psi<PsiMethod>()?.let { psiMethod ->
                context.externalCodeProcessor.addMember(JKMethodData(psiMethod))
            }
        }
    }

    private fun convertField(field: JKField) {
        with(field) {
            mutability = if (modality == FINAL) IMMUTABLE else MUTABLE
            modality = FINAL
            psi<PsiField>()?.let { psiField ->
                context.externalCodeProcessor.addMember(JKFieldDataFromJava(psiField))
            }
        }
    }
}
