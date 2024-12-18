// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.classMembers.AbstractMemberInfoStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.idea.refactoring.resolveDirectSupertypes
import org.jetbrains.kotlin.psi.*

@ApiStatus.Internal
class KotlinMemberInfoStorage(
    classOrObject: KtClassOrObject,
    filter: (KtNamedDeclaration) -> Boolean = { true }
) : AbstractMemberInfoStorage<KtNamedDeclaration, PsiNamedElement, KotlinMemberInfo>(classOrObject, filter) {

    override fun memberConflict(member1: KtNamedDeclaration, member: KtNamedDeclaration): Boolean {
        return KotlinMemberInfoStorageSupport.getInstance().memberConflict(member1, member)
    }

    override fun buildSubClassesMap(aClass: PsiNamedElement) {
        for (superClass in aClass.resolveDirectSupertypes()) {
            if (superClass is KtClass || superClass is PsiClass) {
                getSubclasses(superClass as PsiNamedElement).add(aClass)
                buildSubClassesMap(superClass)
            }
        }
    }

    override fun isInheritor(baseClass: PsiNamedElement, aClass: PsiNamedElement): Boolean {
        return KotlinMemberInfoStorageSupport.getInstance().isInheritor(aClass, baseClass)
    }

    override fun extractClassMembers(aClass: PsiNamedElement, temp: ArrayList<KotlinMemberInfo>) {
        if (aClass is KtClassOrObject) {
            temp += extractClassMembers(aClass, aClass == myClass) { myFilter.includeMember(it) }
        }
    }
}

@ApiStatus.Internal
fun extractClassMembers(
    aClass: KtClassOrObject,
    collectSuperTypeEntries: Boolean = true,
    filter: ((KtNamedDeclaration) -> Boolean)? = null
): List<KotlinMemberInfo> {
    fun KtClassOrObject.extractFromClassBody(
        filter: ((KtNamedDeclaration) -> Boolean)?,
        isCompanion: Boolean,
        result: MutableCollection<KotlinMemberInfo>
    ) {
        declarations
            .asSequence()
            .filter {
                it is KtNamedDeclaration
                        && it !is KtConstructor<*>
                        && !(it is KtObjectDeclaration && it.isCompanion())
                        && (filter == null || filter(it))
            }
            .mapTo(result) { KotlinMemberInfo(it as KtNamedDeclaration, isCompanionMember = isCompanion) }
    }

    val result = ArrayList<KotlinMemberInfo>()

    if (collectSuperTypeEntries) {
        aClass.resolveDirectSupertypes()
            .mapNotNull { classPsi ->
                when (classPsi) {
                    is KtClass -> classPsi
                    is PsiClass -> KtPsiClassWrapper(classPsi)
                    else -> null
                }
            }
            .filter { it.isInterfaceClass() }
            .mapTo(result) { KotlinMemberInfo(it, true) }
    }

    aClass.primaryConstructor
        ?.valueParameters
        ?.asSequence()
        ?.filter { it.hasValOrVar() }
        ?.mapTo(result) { KotlinMemberInfo(it) }

    aClass.extractFromClassBody(filter, false, result)
    (aClass as? KtClass)?.companionObjects?.firstOrNull()?.extractFromClassBody(filter, true, result)

    return result
}