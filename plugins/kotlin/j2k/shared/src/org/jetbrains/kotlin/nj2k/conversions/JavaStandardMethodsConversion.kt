// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.psi
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.Modality.FINAL
import org.jetbrains.kotlin.nj2k.tree.Modality.OPEN
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.OVERRIDE
import org.jetbrains.kotlin.nj2k.tree.Visibility.PROTECTED
import org.jetbrains.kotlin.nj2k.types.JKClassType
import org.jetbrains.kotlin.nj2k.types.JKJavaVoidType
import org.jetbrains.kotlin.nj2k.types.fqName
import org.jetbrains.kotlin.nj2k.types.updateNullability

class JavaStandardMethodsConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element is JKClass) {
            element.classBody.declarations.forEach {
                when {
                    it !is JKMethodImpl -> return@forEach
                    it.isToString() -> it.fixToString()
                    it.isFinalize() -> it.fixFinalize()
                    it.isClone() -> it.fixClone()
                }
            }
        }
        return recurse(element)
    }

    private fun JKMethod.isToString(): Boolean =
        name.value == "toString" && parameters.isEmpty() && returnType.type.fqName == "java.lang.String"

    private fun JKMethod.isFinalize(): Boolean =
        name.value == "finalize" && parameters.isEmpty() && returnType.type == JKJavaVoidType

    private fun JKMethod.isClone(): Boolean =
        name.value == "clone" && parameters.isEmpty() && returnType.type.fqName == "java.lang.Object"

    private fun JKMethod.fixToString() {
        val type = (returnType.type as? JKClassType)?.updateNullability(NotNull) ?: return
        returnType = JKTypeElement(type, returnType::annotationList.detached())
    }

    private fun JKMethod.fixFinalize() {
        if (!hasOtherModifier(OVERRIDE)) return

        val superMethods = psi<PsiMethod>()?.findSuperMethods() ?: return
        val overridesBuiltInFinalize = superMethods.any { it.containingClass?.kotlinFqName?.asString() == "java.lang.Object" }
        if (!overridesBuiltInFinalize) {
            // Here we consider only methods that override the java.lang.Object's 'finalize' directly.
            // The overrides of _overrides_ of java.lang.Object's 'finalize' are considered regular methods
            // that don't need any special handling.
            return
        }

        modality = if (parentOfType<JKClass>()?.modality == OPEN) OPEN else FINAL

        // In Kotlin, 'finalize' is not technically an override, so the 'override' modifier should be removed.
        // Also, 'protected' visibility should not be considered redundant and should be preserved.
        otherModifierElements -= otherModifierElements.first { it.otherModifier == OVERRIDE }

        if (visibility == PROTECTED) {
            hasRedundantVisibility = false
        }
    }

    private fun JKMethod.fixClone() {
        val type = (returnType.type as? JKClassType)?.updateNullability(NotNull) ?: return
        returnType = JKTypeElement(type, returnType::annotationList.detached())

        val containingClass = parentOfType<JKClass>() ?: return
        val cloneableClass = findJavaLangCloneable() ?: return
        val directlyImplementsCloneable = containingClass.psi<PsiClass>()?.isInheritor(cloneableClass, false) ?: return
        val psiMethod = psi<PsiMethod>() ?: return
        val hasCloneableInSuperClasses = psiMethod
            .findSuperMethods()
            .any { superMethod -> superMethod.containingClass?.kotlinFqName?.asString() != "java.lang.Object" }

        if (directlyImplementsCloneable && hasCloneableInSuperClasses) {
            val directCloneableSupertype = containingClass.inheritance.implements.find { typeElement ->
                typeElement.type.fqName == "java.lang.Cloneable"
            } ?: return
            containingClass.inheritance.implements -= directCloneableSupertype
        } else if (!directlyImplementsCloneable && !hasCloneableInSuperClasses) {
            containingClass.inheritance.implements += JKTypeElement(
                JKClassType(
                    context.symbolProvider.provideClassSymbol("kotlin.Cloneable"),
                    nullability = NotNull
                )
            )
        }
    }

    private fun findJavaLangCloneable(): PsiClass? = JavaPsiFacade.getInstance(context.project)
        .findClass("java.lang.Cloneable", GlobalSearchScope.allScope(context.project))
}
