// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.backwardRefs.LanguageCompilerRefAdapter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.NameEnumerator
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasBody
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import org.jetbrains.kotlin.resolve.annotations.JVM_STATIC_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.resolve.jvm.annotations.JVM_OVERLOADS_FQ_NAME

class KotlinCompilerRefHelper : LanguageCompilerRefAdapter.ExternalLanguageHelper() {
    override fun getAffectedFileTypes(): Set<FileType> = setOf(KotlinFileType.INSTANCE)
    override fun asCompilerRef(element: PsiElement, names: NameEnumerator): CompilerRef? = asCompilerRefs(element, names)?.singleOrNull()
    override fun asCompilerRefs(element: PsiElement, names: NameEnumerator): List<CompilerRef>? =
        when (val originalElement = element.unwrapped) {
            is KtClass -> originalElement.asClassCompilerRef(names)?.let(::listOf)
            is KtObjectDeclaration -> originalElement.asObjectCompilerRefs(names)
            is KtConstructor<*> -> originalElement.asConstructorCompilerRef(names)
            is KtCallableDeclaration -> originalElement.asCallableCompilerRefs(names)
            else -> null
        }

    override fun getHierarchyRestrictedToLibraryScope(
        baseRef: CompilerRef,
        basePsi: PsiElement,
        names: NameEnumerator,
        libraryScope: GlobalSearchScope
    ): List<CompilerRef> {
        val baseClass = when (basePsi) {
            is KtClassOrObject, is PsiClass -> basePsi
            is PsiMember -> basePsi.containingClass
            is KtCallableDeclaration -> basePsi.containingClassOrObject?.takeUnless { it is KtObjectDeclaration }
            else -> null
        } ?: return emptyList()

        val overridden = mutableListOf<CompilerRef>()
        val processor = Processor { psiClass: PsiClass ->
            psiClass.takeUnless { it.hasModifierProperty(PsiModifier.PRIVATE) }
                ?.let { runReadAction { it.qualifiedName } }
                ?.let { names.tryEnumerate(it) }
                ?.let { overridden.add(baseRef.override(it)) }

            true
        }

        HierarchySearchRequest(
            originalElement = baseClass,
            searchScope = libraryScope,
            searchDeeply = true,
        ).searchInheritors().forEach(processor)

        return overridden
    }
}

private fun KtCallableDeclaration.asCallableCompilerRefs(names: NameEnumerator): List<CompilerRef>? {
    if (isTopLevelKtOrJavaMember()) return asTopLevelCompilerRefs(names)
    val containingClassOrObject = containingClassOrObject ?: return null
    return when (containingClassOrObject) {
        is KtClass -> asClassMemberCompilerRefs(containingClassOrObject, names)
        is KtObjectDeclaration -> asObjectMemberCompilerRefs(containingClassOrObject, names)
        else -> null
    }
}

private fun KtCallableDeclaration.asClassMemberCompilerRefs(
    containingClass: KtClass,
    names: NameEnumerator,
): List<CompilerRef>? = when (this) {
    is KtNamedFunction -> asClassMemberFunctionCompilerRefs(containingClass, names)
    is KtProperty -> asClassMemberPropertyCompilerRefs(containingClass, names)
    is KtParameter -> asParameterCompilerRefs(containingClass, names)
    else -> null
}

private fun KtCallableDeclaration.asObjectMemberCompilerRefs(
    containingObject: KtObjectDeclaration,
    names: NameEnumerator,
): List<CompilerRef>? = when (this) {
    is KtNamedFunction -> asObjectMemberFunctionCompilerRefs(containingObject, names)
    is KtProperty -> asObjectMemberPropertyCompilerRefs(containingObject, names)
    else -> null
}

private fun KtCallableDeclaration.asTopLevelCompilerRefs(names: NameEnumerator): List<CompilerRef.CompilerMember>? =
    containingKtFile.javaFileFacadeFqName.asString().let(names::tryEnumerate).let { qualifierId ->
        when (this) {
            is KtNamedFunction -> asFunctionCompilerRefs(qualifierId, names)
            is KtProperty -> asPropertyCompilerRefs(qualifierId, names)
            else -> null
        }
    }

private fun KtClassOrObject.asClassCompilerRef(names: NameEnumerator): CompilerRef.CompilerClassHierarchyElementDef? =
    qualifierId(names)?.let(CompilerRef::JavaCompilerClassRef)

private fun KtClassOrObject.qualifier(): String? = jvmFqName
private fun KtClassOrObject.qualifierId(names: NameEnumerator): Int? = qualifier()?.let(names::tryEnumerate)

private fun KtObjectDeclaration.asObjectCompilerRefs(names: NameEnumerator): List<CompilerRef.NamedCompilerRef>? {
    val classCompilerRef = asClassCompilerRef(names) ?: return null
    val instanceField = if (isCompanion()) {
        asCompanionCompilerRef(names)
    } else {
        CompilerRef.JavaCompilerFieldRef(classCompilerRef.name, names.tryEnumerate("INSTANCE"))
    }

    return listOfNotNull(classCompilerRef, instanceField)
}

private fun KtObjectDeclaration.asCompanionCompilerRef(names: NameEnumerator): CompilerRef.NamedCompilerRef? {
    val name = name ?: return null
    val qualifierId = containingClassOrObject?.qualifierId(names) ?: return null
    return CompilerRef.JavaCompilerFieldRef(qualifierId, names.tryEnumerate(name))
}

private fun KtConstructor<*>.asConstructorCompilerRef(names: NameEnumerator): List<CompilerRef.CompilerMember>? {
    val qualifierId = getContainingClassOrObject().qualifierId(names) ?: return null
    val nameId = names.tryEnumerate("<init>")
    return asCompilerRefsWithJvmOverloads(qualifierId, nameId)
}

private fun KtNamedFunction.asFunctionCompilerRefs(
    qualifierId: Int,
    names: NameEnumerator,
    isDefaultImplsMember: Boolean = false,
): List<CompilerRef.CompilerMember> {
    val nameId = names.tryEnumerate(jvmName ?: name)
    return asCompilerRefsWithJvmOverloads(qualifierId, nameId, isDefaultImplsMember)
}

private fun KtNamedFunction.asClassMemberFunctionCompilerRefs(
    containingClass: KtClass,
    names: NameEnumerator,
): List<CompilerRef>? = asClassMemberCompilerRefs(
    containingClass = containingClass,
    names = names,
    methodHandler = { qualifierId -> asFunctionCompilerRefs(qualifierId, names) },
    defaultMethodHandler = { qualifierId -> asFunctionCompilerRefs(qualifierId, names, isDefaultImplsMember = true) },
)

private fun KtProperty.asClassMemberPropertyCompilerRefs(
    containingClass: KtClass,
    names: NameEnumerator,
): List<CompilerRef>? = asClassMemberCompilerRefs(
    containingClass = containingClass,
    names = names,
    methodHandler = { qualifierId -> asPropertyCompilerRefs(qualifierId, names) },
    defaultMethodHandler = { qualifierId -> asPropertyCompilerRefs(qualifierId, names, fieldOwnerId = null, isDefaultImplsMember = true) },
)

private fun KtCallableDeclaration.asClassMemberCompilerRefs(
    containingClass: KtClass,
    names: NameEnumerator,
    methodHandler: (qualifierId: Int) -> List<CompilerRef>?,
    defaultMethodHandler: (qualifierId: Int) -> List<CompilerRef>? = methodHandler,
): List<CompilerRef>? {
    val qualifier = containingClass.qualifier() ?: return null
    val compilerMembers = methodHandler(names.tryEnumerate(qualifier))
    if (!containingClass.isInterface() || !hasBody()) return compilerMembers

    val defaultImplQualifier = qualifier + JvmAbi.DEFAULT_IMPLS_SUFFIX
    val defaultImplMembers = defaultMethodHandler(names.tryEnumerate(defaultImplQualifier))
        ?: return compilerMembers

    return compilerMembers?.plus(defaultImplMembers) ?: defaultImplMembers
}

private fun KtNamedFunction.asObjectMemberFunctionCompilerRefs(
    containingObject: KtObjectDeclaration,
    names: NameEnumerator,
): List<CompilerRef.CompilerMember>? {
    val qualifierId = containingObject.qualifierId(names) ?: return null
    val compilerMembers = asFunctionCompilerRefs(qualifierId, names)
    val additionalQualifierId = containingObject.takeIf { hasJvmStaticAnnotation() }
        ?.containingClassOrObject
        ?.qualifierId(names)
        ?: return compilerMembers

    return compilerMembers + asFunctionCompilerRefs(additionalQualifierId, names)
}

private fun KtFunction.asCompilerRefsWithJvmOverloads(
    qualifierId: Int,
    nameId: Int,
    isDefaultImplsMember: Boolean = false,
): List<CompilerRef.CompilerMember> {
    val numberOfArguments = numberOfArguments(countReceiver = true) + (1.takeIf { isDefaultImplsMember } ?: 0)
    if (!hasJvmOverloadsAnnotation()) {
        val mainMethodRef = CompilerRef.JavaCompilerMethodRef(qualifierId, nameId, numberOfArguments)
        return if (this is KtPrimaryConstructor && valueParameters.all(KtParameter::hasDefaultValue)) {
            listOf(mainMethodRef, CompilerRef.JavaCompilerMethodRef(qualifierId, nameId, 0))
        } else {
            listOf(mainMethodRef)
        }
    }

    return numberOfArguments.minus(valueParameters.count(KtParameter::hasDefaultValue)).rangeTo(numberOfArguments).map {
        CompilerRef.JavaCompilerMethodRef(qualifierId, nameId, it)
    }
}

private fun KtProperty.asObjectMemberPropertyCompilerRefs(
    containingObject: KtObjectDeclaration,
    names: NameEnumerator,
): List<CompilerRef.CompilerMember>? {
    val qualifierId = containingObject.qualifierId(names) ?: return null
    if (!containingObject.isCompanion()) {
        return asPropertyCompilerRefs(qualifierId, names)
    }

    val fieldOwnerId = containingObject.containingClassOrObject?.qualifierId(names)
    val compilerMembers = asPropertyCompilerRefs(qualifierId, names, fieldOwnerId)
    if (!hasJvmStaticAnnotation() || fieldOwnerId == null) return compilerMembers

    val staticMembers = asPropertyCompilerRefs(fieldOwnerId, names, fieldOwnerId = null) ?: return compilerMembers
    return compilerMembers?.plus(staticMembers) ?: staticMembers
}

private fun KtProperty.asPropertyCompilerRefs(
    qualifierId: Int,
    names: NameEnumerator,
    fieldOwnerId: Int? = qualifierId,
    isDefaultImplsMember: Boolean = false,
): List<CompilerRef.CompilerMember>? = asPropertyOrParameterCompilerRefs(qualifierId, names, isVar, fieldOwnerId, isDefaultImplsMember)

private fun KtParameter.asParameterCompilerRefs(
    containingClass: KtClass,
    names: NameEnumerator,
): List<CompilerRef.CompilerMember>? {
    if (!hasValOrVar()) return null

    val qualifierId = containingClass.qualifierId(names) ?: return null
    if (containingClassOrObject?.isAnnotation() == true) {
        val name = name ?: return null
        return listOf(CompilerRef.JavaCompilerMethodRef(qualifierId, names.tryEnumerate(name), 0))
    }

    val compilerMembers = asPropertyOrParameterCompilerRefs(qualifierId, names, isMutable)
    val componentFunctionMember = asComponentFunctionName?.let {
        CompilerRef.JavaCompilerMethodRef(qualifierId, names.tryEnumerate(it), 0)
    } ?: return compilerMembers

    return compilerMembers?.plus(componentFunctionMember) ?: listOf(componentFunctionMember)
}

private fun <T> T.asPropertyOrParameterCompilerRefs(
    qualifierId: Int,
    names: NameEnumerator,
    isMutable: Boolean,
    fieldOwnerId: Int? = qualifierId,
    isDefaultImplsMember: Boolean = false,
): List<CompilerRef.CompilerMember>? where T : KtCallableDeclaration, T : KtValVarKeywordOwner {
    val name = name ?: return null
    if (fieldOwnerId != null && (hasModifier(KtTokens.CONST_KEYWORD) || hasJvmFieldAnnotation())) {
        return listOf(CompilerRef.JavaCompilerFieldRef(fieldOwnerId, names.tryEnumerate(name)))
    }

    val field = if (fieldOwnerId != null && hasModifier(KtTokens.LATEINIT_KEYWORD)) {
        CompilerRef.JavaCompilerFieldRef(fieldOwnerId, names.tryEnumerate(name))
    } else {
        null
    }

    val numberOfArguments = numberOfArguments(countReceiver = true) + (1.takeIf { isDefaultImplsMember } ?: 0)
    val getter = CompilerRef.JavaCompilerMethodRef(
        qualifierId,
        names.tryEnumerate(jvmGetterName ?: JvmAbi.getterName(name)),
        numberOfArguments,
    )

    val setter = if (isMutable)
        CompilerRef.JavaCompilerMethodRef(
            qualifierId,
            names.tryEnumerate(jvmSetterName ?: JvmAbi.setterName(name)),
            numberOfArguments + 1,
        )
    else
        null

    return listOfNotNull(field, getter, setter)
}

private fun KtAnnotated.hasJvmStaticAnnotation(): Boolean = hasAnnotationWithShortName(JVM_STATIC_ANNOTATION_FQ_NAME.shortName())
private fun KtAnnotated.hasJvmOverloadsAnnotation(): Boolean = hasAnnotationWithShortName(JVM_OVERLOADS_FQ_NAME.shortName())
private fun KtAnnotated.hasJvmFieldAnnotation(): Boolean = hasAnnotationWithShortName(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME.shortName())