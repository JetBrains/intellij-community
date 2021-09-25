// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.j2k.ast.Nullability
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKMethodData
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.throwAnnotation
import org.jetbrains.kotlin.nj2k.tree.JKMethodImpl
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.types.updateNullabilityRecursively

class JavaMethodToKotlinFunctionConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKMethodImpl) return recurse(element)

        if (element.throwsList.isNotEmpty()) {
            element.annotationList.annotations +=
                throwAnnotation(
                    element.throwsList.map { it.type.updateNullabilityRecursively(Nullability.NotNull) },
                    symbolProvider
                )
        }

        element.psi<PsiMethod>()?.let { psi ->
            context.externalCodeProcessor.addMember(JKMethodData(psi))
        }

        return recurse(element)
    }
}