// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReferenceList
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.classMembers.MemberInfoBase
import com.intellij.refactoring.util.classMembers.MemberInfo
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

@OptIn(KaAllowAnalysisOnEdt::class)
sealed class KotlinMemberInfo private constructor(
    member: KtElement,
    val isSuperClass: Boolean,
    val isCompanionMember: Boolean
) : MemberInfoBase<KtElement>(member) {
    class Declaration @JvmOverloads constructor(
        declaration: KtNamedDeclaration,
        isSuperClass: Boolean = false,
        isCompanionMember: Boolean = false
    ) : KotlinMemberInfo(declaration, isSuperClass, isCompanionMember) {
        val declaration: KtNamedDeclaration get() = member as KtNamedDeclaration
    }

    class SuperType(superTypeEntry: KtSuperTypeListEntry) : KotlinMemberInfo(superTypeEntry, true, false) {
        val superTypeEntry: KtSuperTypeListEntry get() = member as KtSuperTypeListEntry
    }

    init {
        isStatic = member.parent is KtFile
        when {
            isSuperClass && (member is KtClass || member is KtSuperTypeListEntry) -> {
                // A super type entry is not a named element, so its label comes from the written type; only the
                // interface/class distinction, which drives `overrides`, needs the type to be resolved.
                val name: String
                val isInterface: Boolean
                if (member is KtSuperTypeListEntry) {
                    name = member.typeAsUserType?.referencedName ?: member.typeReference?.text ?: member.text
                    isInterface = member.resolveSuperTypeDeclaration()?.isInterfaceClass() == true
                } else {
                    name = (member as KtClass).name ?: member.text
                    isInterface = member.isInterface()
                }
                if (isInterface) {
                    displayName = RefactoringBundle.message("member.info.implements.0", name)
                    overrides = false
                } else {
                    displayName = RefactoringBundle.message("member.info.extends.0", name)
                    overrides = true
                }
            }
            member is KtNamedDeclaration -> {
                displayName = allowAnalysisOnEdt { KotlinMemberInfoSupport.getInstance().renderMemberInfo(member) }
                if (isCompanionMember) {
                    displayName = KotlinBundle.message("member.info.companion.0", displayName)
                }
                overrides = allowAnalysisOnEdt { KotlinMemberInfoSupport.getInstance().getOverrides(member) }
            }
            else -> error("Unexpected member info element: ${member.javaClass.name}")
        }
    }
}

/**
 * The class or interface this super type entry refers to, as source PSI: a [KtClassOrObject] for Kotlin and a
 * [PsiClass] for Java. Unlike [lightElementForMemberInfo] this never builds a light class.
 */
@OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
fun KtSuperTypeListEntry.resolveSuperTypeDeclaration(): PsiElement? {
    val entry = this
    return allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            analyze(entry) { entry.typeReference?.type?.expandedSymbol?.psi }
        }
    }
}

/**
 * The [PsiClass] view of a resolved super type declaration, building a light class only for Kotlin declarations.
 */
private fun PsiElement?.toPsiClass(): PsiClass? = when (this) {
    is PsiClass -> this
    is KtClassOrObject -> toLightClass()
    else -> null
}

/**
 * The super type entry of [sourceClass] referring to [superDeclaration], which must be source PSI as returned by
 * [org.jetbrains.kotlin.asJava.unwrapped]. Comparing source declarations rather than light classes keeps this free of
 * light class construction; all entries are resolved within a single analysis session.
 */
@OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
fun findSuperTypeEntry(sourceClass: KtClassOrObject, superDeclaration: PsiElement?): KtSuperTypeEntry? {
    if (superDeclaration == null) return null
    val entries = sourceClass.superTypeListEntries.filterIsInstance<KtSuperTypeEntry>().ifEmpty { return null }
    return allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            analyze(sourceClass) {
                entries.firstOrNull { it.typeReference?.type?.expandedSymbol?.psi == superDeclaration }
            }
        }
    }
}

fun lightElementForMemberInfo(declaration: KtElement?): PsiMember? {
    return when (declaration) {
        is KtNamedFunction -> declaration.getRepresentativeLightMethod()
        is KtProperty, is KtParameter -> declaration.toLightElements().let { lightElements ->
            lightElements.firstIsInstanceOrNull<PsiMethod>()
                ?: lightElements.firstIsInstanceOrNull<PsiField>()
                // Fallback for value class properties: their accessors are not generated as regular
                // PsiMethods, so toLightElements() returns neither a PsiMethod nor a PsiField for them.
                // Without this, such properties resolve to null and are silently dropped from
                // refactorings like Extract Interface/Superclass.
                ?: LightClassUtil.getLightClassBackingField(declaration)
        }
        is KtClassOrObject -> declaration.toLightClass()
        is KtSuperTypeListEntry -> declaration.resolveSuperTypeDeclaration().toPsiClass()
        else -> null
    }
}

/**
 * The reference list of [sourceClass] holding this super type entry. Every supertype of a Java interface lives in its
 * extends list, whereas a class keeps only its superclass there and its interfaces in the implements list.
 */
private fun sourceReferenceListFor(sourceClass: PsiClass, superTypeDeclaration: PsiElement?): PsiReferenceList? = when {
    sourceClass.isInterface -> sourceClass.extendsList
    superTypeDeclaration?.isInterfaceClass() == true -> sourceClass.implementsList
    else -> sourceClass.extendsList
}

fun MemberInfoBase<out KtElement>.toJavaMemberInfo(): MemberInfo? {
    val declaration = member
    val superTypeEntry = declaration as? KtSuperTypeListEntry
    // Resolve the entry once and derive both the light class and the reference list from it.
    val superTypeDeclaration = superTypeEntry?.resolveSuperTypeDeclaration()
    val psiMember: PsiMember? =
        if (superTypeEntry != null) superTypeDeclaration.toPsiClass() else lightElementForMemberInfo(declaration)
    val sourceReferenceList = superTypeEntry
        ?.getStrictParentOfType<KtClassOrObject>()
        ?.toLightClass()
        ?.let { sourceReferenceListFor(it, superTypeDeclaration) }
    val info = MemberInfo(psiMember ?: return null, psiMember is PsiClass && overrides != null, sourceReferenceList)
    info.isToAbstract = isToAbstract
    info.isChecked = isChecked
    return info
}

@Suppress("unused") // used in third-party plugins
fun MemberInfo.toKotlinMemberInfo(): KotlinMemberInfo? {
    val declaration = member.unwrapped as? KtNamedDeclaration ?: return null
    return KotlinMemberInfo.Declaration(declaration, declaration is KtClass && overrides != null).apply {
        this.isToAbstract = this@toKotlinMemberInfo.isToAbstract
    }
}

// Applies to KtClassOrObject and PsiClass
@NlsSafe
fun PsiNamedElement.qualifiedClassNameForRendering(): String {
    val fqName = when (this) {
        is KtClassOrObject -> fqName?.asString()
        is PsiClass -> qualifiedName
        else -> throw AssertionError("Not a class: ${getElementTextWithContext()}")
    }
    return fqName ?: name ?: KotlinBundle.message("text.anonymous")
}

fun KotlinMemberInfo.getChildrenToAnalyze(): List<PsiElement> {
    val member = member
    val childrenToCheck = member.allChildren.toMutableList()
    if (isToAbstract && member is KtCallableDeclaration) {
        when (member) {
            is KtNamedFunction -> childrenToCheck.remove(member.bodyExpression as PsiElement?)
            is KtProperty -> {
                childrenToCheck.remove(member.initializer as PsiElement?)
                childrenToCheck.remove(member.delegateExpression as PsiElement?)
                childrenToCheck.removeAll(member.accessors)
            }
        }
    }
    return childrenToCheck
}
