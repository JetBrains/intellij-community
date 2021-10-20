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
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import org.jetbrains.kotlin.resolve.jvm.annotations.JVM_OVERLOADS_FQ_NAME

class KotlinCompilerRefHelper : LanguageCompilerRefAdapter.ExternalLanguageHelper() {
    override fun getAffectedFileTypes(): Set<FileType> = setOf(KotlinFileType.INSTANCE)
    override fun asCompilerRef(element: PsiElement, names: NameEnumerator): CompilerRef? = asCompilerRefs(element, names)?.singleOrNull()
    override fun asCompilerRefs(element: PsiElement, names: NameEnumerator): List<CompilerRef>? =
        when (val originalElement = element.unwrapped) {
            is KtClass -> originalElement.asClassCompilerRef(names)?.let(::listOf)
            is KtObjectDeclaration -> originalElement.asCompilerRefs(names)
            is KtConstructor<*> -> originalElement.asCompilerRef(names)
            is KtCallableDeclaration -> originalElement.asCompilerRefs(names)
            else -> null
        }

    private fun KtCallableDeclaration.asCompilerRefs(names: NameEnumerator): List<CompilerRef>? {
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
    ): List<CompilerRef>? {
        val qualifierId = containingClass.qualifierId(names) ?: return null
        return when (this) {
            is KtNamedFunction -> asCompilerRefs(qualifierId, names)
            is KtProperty -> asCompilerRefs(qualifierId, names)
            is KtParameter -> asCompilerRefs(qualifierId, names)
            else -> null
        }
    }

    private fun KtCallableDeclaration.asObjectMemberCompilerRefs(
        containingObject: KtObjectDeclaration,
        names: NameEnumerator,
    ): List<CompilerRef>? = null

    private fun KtCallableDeclaration.asTopLevelCompilerRefs(names: NameEnumerator): List<CompilerRef.CompilerMember>? =
        containingKtFile.javaFileFacadeFqName.asString().let(names::tryEnumerate).let { qualifierId ->
            when (this) {
                is KtNamedFunction -> asCompilerRefs(qualifierId, names)
                is KtProperty -> asCompilerRefs(qualifierId, names)
                else -> null
            }
        }

    private fun KtClassOrObject.asClassCompilerRef(names: NameEnumerator): CompilerRef.CompilerClassHierarchyElementDef? =
        qualifierId(names)?.let(CompilerRef::JavaCompilerClassRef)

    private fun KtClassOrObject.qualifierId(names: NameEnumerator): Int? = jvmFqName?.let(names::tryEnumerate)

    private fun KtObjectDeclaration.asCompilerRefs(names: NameEnumerator): List<CompilerRef.NamedCompilerRef>? {
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

    private fun KtConstructor<*>.asCompilerRef(names: NameEnumerator): List<CompilerRef.CompilerMember>? {
        val qualifierId = getContainingClassOrObject().qualifierId(names) ?: return null
        val nameId = names.tryEnumerate("<init>")
        return asCompilerRefsWithJvmOverloads(qualifierId, nameId)
    }

    private fun KtNamedFunction.asCompilerRefs(qualifierId: Int, names: NameEnumerator): List<CompilerRef.CompilerMember> {
        val nameId = names.tryEnumerate(jvmName ?: name)
        return asCompilerRefsWithJvmOverloads(qualifierId, nameId)
    }

    private fun KtCallableDeclaration.asCompilerRefsWithJvmOverloads(qualifierId: Int, nameId: Int): List<CompilerRef.CompilerMember> {
        val numberOfArguments = numberOfArguments(countReceiver = true)
        if (!hasAnnotationWithShortName(JVM_OVERLOADS_FQ_NAME.shortName().asString())) {
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

    private fun KtProperty.asCompilerRefs(
        qualifierId: Int,
        names: NameEnumerator,
    ): List<CompilerRef.CompilerMember>? = asCompilerRefs(qualifierId, names, isVar)

    private fun KtParameter.asCompilerRefs(
        qualifierId: Int,
        names: NameEnumerator,
    ): List<CompilerRef.CompilerMember>? {
        if (!hasValOrVar()) return null
        if (containingClassOrObject?.isAnnotation() == true) {
            val name = name ?: return null
            return listOf(CompilerRef.JavaCompilerMethodRef(qualifierId, names.tryEnumerate(name), 0))
        }

        val compilerMembers = asCompilerRefs(qualifierId, names, isMutable)
        val componentFunctionMember = asComponentFunctionName?.let {
            CompilerRef.JavaCompilerMethodRef(qualifierId, names.tryEnumerate(it), 0)
        } ?: return compilerMembers

        return compilerMembers?.plus(componentFunctionMember) ?: listOf(componentFunctionMember)
    }

    private fun <T> T.asCompilerRefs(
        qualifierId: Int,
        names: NameEnumerator,
        isMutable: Boolean,
    ): List<CompilerRef.CompilerMember>? where T : KtCallableDeclaration, T : KtValVarKeywordOwner {
        val name = name ?: return null
        if (hasModifier(KtTokens.CONST_KEYWORD) || hasAnnotationWithShortName(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME.shortName().asString())) {
            return listOf(CompilerRef.JavaCompilerFieldRef(qualifierId, names.tryEnumerate(name)))
        }

        val field = if (hasModifier(KtTokens.LATEINIT_KEYWORD)) {
            CompilerRef.JavaCompilerFieldRef(qualifierId, names.tryEnumerate(name))
        } else {
            null
        }

        val numberOfArguments = numberOfArguments(countReceiver = true)
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
