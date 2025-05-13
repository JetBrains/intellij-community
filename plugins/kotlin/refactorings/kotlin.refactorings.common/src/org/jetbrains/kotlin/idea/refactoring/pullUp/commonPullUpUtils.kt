// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.lightElementForMemberInfo
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

@ApiStatus.Internal
fun KtProperty.mustBeAbstractInInterface(): Boolean =
    hasInitializer() || hasDelegate() || (!hasInitializer() && !hasDelegate() && accessors.isEmpty())

@ApiStatus.Internal
fun KtNamedDeclaration.isAbstractInInterface(originalClass: KtClassOrObject): Boolean =
    originalClass is KtClass && originalClass.isInterface() && isAbstract()

@ApiStatus.Internal
fun KtNamedDeclaration.canMoveMemberToJavaClass(targetClass: PsiClass): Boolean {
    return when (this) {
        is KtProperty, is KtParameter -> {
            if (targetClass.isInterface) return false
            if (hasModifier(KtTokens.OPEN_KEYWORD) || hasModifier(KtTokens.ABSTRACT_KEYWORD)) return false
            if (this is KtProperty && (accessors.isNotEmpty() || delegateExpression != null)) return false
            true
        }
        is KtNamedFunction -> valueParameters.all { it.defaultValue == null }
        else -> false
    }
}

@ApiStatus.Internal
fun getInterfaceContainmentVerifier(getMemberInfos: () -> List<KotlinMemberInfo>): (KtNamedDeclaration) -> Boolean = result@{ member ->
    val psiMethodToCheck = lightElementForMemberInfo(member) as? PsiMethod ?: return@result false
    getMemberInfos().any {
        if (!it.isSuperClass || it.overrides != false) return@any false

        val psiSuperInterface = (it.member as? KtClass)?.toLightClass()
        psiSuperInterface?.findMethodBySignature(psiMethodToCheck, true) != null
    }
}

fun addMemberToTarget(targetMember: KtNamedDeclaration, targetClass: KtClassOrObject): KtNamedDeclaration {
    if (targetClass is KtClass && targetClass.isInterface()) {
        targetMember.removeModifier(KtTokens.FINAL_KEYWORD)
    }

    if (targetMember is KtParameter) {
        val parameterList = (targetClass as KtClass).createPrimaryConstructorIfAbsent().valueParameterList!!
        val anchor = parameterList.parameters.firstOrNull { it.isVarArg || it.hasDefaultValue() }
        return parameterList.addParameterBefore(targetMember, anchor)
    }

    val anchor = targetClass.declarations.asSequence().filterIsInstance(targetMember::class.java).lastOrNull()
    return when {
        anchor == null && targetMember is KtProperty -> targetClass.addDeclarationBefore(targetMember, null)
        else -> targetClass.addDeclarationAfter(targetMember, anchor)
    }
}

fun doAddCallableMember(
    memberCopy: KtCallableDeclaration,
    clashingSuper: KtCallableDeclaration?,
    targetClass: KtClassOrObject
): KtCallableDeclaration {
    val memberToAdd = if (memberCopy is KtParameter && memberCopy.needToBeAbstract(targetClass)) memberCopy.toProperty() else memberCopy

    if (clashingSuper != null && clashingSuper.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
        return clashingSuper.replaced(if (memberToAdd is KtParameter && clashingSuper is KtProperty) memberToAdd.toProperty() else memberToAdd)
    }

    return addMemberToTarget(memberToAdd, targetClass) as KtCallableDeclaration
}

// TODO: Formatting rules don't apply here for some reason
fun KtNamedDeclaration.addAnnotationWithSpace(annotationEntry: KtAnnotationEntry): KtAnnotationEntry {
    val result = addAnnotationEntry(annotationEntry)
    addAfter(KtPsiFactory(project).createWhiteSpace(), modifierList)
    return result
}

fun KtClass.makeAbstract() {
    if (!isInterface()) {
        addModifier(KtTokens.ABSTRACT_KEYWORD)
    }
}

private fun KtParameter.needToBeAbstract(targetClass: KtClassOrObject): Boolean {
    return hasModifier(KtTokens.ABSTRACT_KEYWORD) || targetClass is KtClass && targetClass.isInterface()
}

private fun KtParameter.toProperty(): KtProperty =
    KtPsiFactory(project)
        .createProperty(text)
        .also {
            val originalTypeRef = typeReference
            val generatedTypeRef = it.typeReference
            if (originalTypeRef != null && generatedTypeRef != null) {
                // Preserve copyable user data of original type reference
                generatedTypeRef.replace(originalTypeRef)
            }
        }
