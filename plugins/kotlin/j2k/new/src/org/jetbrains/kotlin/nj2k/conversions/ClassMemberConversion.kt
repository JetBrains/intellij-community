// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.j2k.ast.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKFieldDataFromJava
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKPhysicalMethodData
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.Modality.FINAL
import org.jetbrains.kotlin.nj2k.tree.Mutability.IMMUTABLE
import org.jetbrains.kotlin.nj2k.tree.Mutability.MUTABLE
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.STATIC
import org.jetbrains.kotlin.nj2k.types.JKJavaArrayType
import org.jetbrains.kotlin.nj2k.types.arrayInnerType
import org.jetbrains.kotlin.nj2k.types.isStringType
import org.jetbrains.kotlin.nj2k.types.updateNullabilityRecursively

class ClassMemberConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        when (element) {
            is JKMethodImpl -> element.convert()
            is JKField -> element.convert()
        }
        return recurse(element)
    }

    private fun JKMethodImpl.convert() {
        if (throwsList.isNotEmpty()) {
            annotationList.annotations +=
                throwsAnnotation(throwsList.map { it.type.updateNullabilityRecursively(NotNull) }, symbolProvider)
        }
        if (isMainFunctionDeclaration()) {
            annotationList.annotations += jvmAnnotation("JvmStatic", symbolProvider)
            parameters.single().let {
                it.type.type = JKJavaArrayType(typeFactory.types.string, NotNull)
                it.isVarArgs = false
            }
        }
        psi<PsiMethod>()?.let { psiMethod ->
            context.externalCodeProcessor.addMember(JKPhysicalMethodData(psiMethod))
        }
    }

    private fun JKMethodImpl.isMainFunctionDeclaration(): Boolean {
        if (name.value != "main") return false
        if (!hasOtherModifier(STATIC)) return false
        val parameter = parameters.singleOrNull() ?: return false
        return when {
            parameter.type.type.arrayInnerType()?.isStringType() == true -> true
            parameter.isVarArgs && parameter.type.type.isStringType() -> true
            else -> false
        }
    }

    private fun JKField.convert() {
        mutability = if (modality == FINAL) IMMUTABLE else MUTABLE
        modality = FINAL
        psi<PsiField>()?.let { psiField ->
            context.externalCodeProcessor.addMember(JKFieldDataFromJava(psiField))
        }
    }
}
