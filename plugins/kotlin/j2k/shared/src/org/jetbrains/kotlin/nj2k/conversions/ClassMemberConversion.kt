// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKFieldDataFromJava
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.JKPhysicalMethodData
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.Visibility.PRIVATE
import org.jetbrains.kotlin.nj2k.tree.Mutability.*
import org.jetbrains.kotlin.nj2k.tree.Modality.FINAL
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.STATIC
import org.jetbrains.kotlin.nj2k.types.JKJavaArrayType
import org.jetbrains.kotlin.nj2k.types.arrayInnerType
import org.jetbrains.kotlin.nj2k.types.isStringType
import org.jetbrains.kotlin.nj2k.types.updateNullabilityRecursively

class ClassMemberConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        when (element) {
            is JKMethodImpl -> element.convert()
            is JKField -> element.convert()
        }
        return recurse(element)
    }

    context(_: KaSession)
    private fun JKMethodImpl.convert() {
        removeStaticModifierFromAnonymousClassMember()

        if (throwsList.isNotEmpty()) {
            annotationList.annotations +=
                throwsAnnotation(throwsList.map { it.type.updateNullabilityRecursively(NotNull) }, symbolProvider)
        }

        if (isMainFunctionDeclaration()) {
            val parameter = parameters.single()
            parameter.type.type = JKJavaArrayType(typeFactory.types.string, NotNull)
            parameter.isVarArgs = false

            if (isTopLevel()) {
                if (!parameter.hasUsages(scope = this, context)) {
                    // simple top-level parameterless `main`
                    parameters = emptyList()
                }
            } else {
                annotationList.annotations += jvmAnnotation("JvmStatic", symbolProvider)
            }
        }

        if (isExternallyAccessible()) {
            val psiMethod = psi<PsiMethod>() ?: return
            context.externalCodeProcessor.addMember(JKPhysicalMethodData(psiMethod))
        }
    }

    private fun JKDeclaration.removeStaticModifierFromAnonymousClassMember() {
        (this as? JKOtherModifiersOwner)?.elementByModifier(STATIC)?.let { static ->
            val grandParent = parent?.parent
            if (grandParent is JKNewExpression && grandParent.isAnonymousClass) {
                otherModifierElements -= static
            }
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

    context(_: KaSession)
    private fun JKField.convert() {
        removeStaticModifierFromAnonymousClassMember()
        val hasMutableAnnotation = annotationList.annotations.any { MUTABLE_ANNOTATIONS.contains(it.classSymbol.fqName) }
        mutability = when {
            modality == FINAL -> IMMUTABLE
            hasMutableAnnotation -> MUTABLE
            mutability != UNKNOWN -> mutability
            else -> UNKNOWN
        }
        modality = FINAL

        if (isExternallyAccessible()) {
            val psiField = psi<PsiField>() ?: return
            context.externalCodeProcessor.addMember(JKFieldDataFromJava(psiField))
        }
    }

    private fun JKDeclaration.isExternallyAccessible(): Boolean {
        require(this is JKVisibilityOwner)
        val container = parentOfType<JKClass>() ?: return false
        if (container.visibility == PRIVATE) return false
        if (container.isLocalClass()) return false
        if (this is JKMethod && visibility == PRIVATE) {
            // This condition does not apply to private fields, that may be later merged
            // with public accessors, and so become public Kotlin properties
            return false
        }

        return true
    }
}