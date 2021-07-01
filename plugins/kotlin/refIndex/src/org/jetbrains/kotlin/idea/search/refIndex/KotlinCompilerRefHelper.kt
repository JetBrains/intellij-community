// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.backwardRefs.LanguageCompilerRefAdapter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
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
import org.jetbrains.kotlin.idea.util.jvmName
import org.jetbrains.kotlin.idea.util.numberOfArguments
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember

class KotlinCompilerRefHelper : LanguageCompilerRefAdapter.ExternalLanguageHelper() {
    override fun getAffectedFileTypes(): Set<FileType> = setOf(KotlinFileType.INSTANCE)
    override fun asCompilerRef(element: PsiElement, names: NameEnumerator): CompilerRef? = null
    override fun asCompilerRefs(element: PsiElement, names: NameEnumerator): List<CompilerRef>? =
        when (val originalElement = element.unwrapped) {
            is KtClassOrObject -> originalElement.asCompilerRef(names)?.let(::listOf)
            is KtConstructor<*> -> originalElement.asCompilerRef(names)?.let(::listOf)
            is KtCallableDeclaration -> originalElement.takeIf { it.isTopLevelKtOrJavaMember() }
                ?.containingKtFile
                ?.javaFileFacadeFqName
                ?.asString()
                ?.let { qualifier ->
                    when (originalElement) {
                        is KtNamedFunction -> originalElement.asCompilerRef(qualifier, names).let(::listOf)
                        is KtProperty -> originalElement.asCompilerRef(qualifier, names)
                        else -> null
                    }
                }

            else -> null
        }

    private fun KtClassOrObject.asCompilerRef(names: NameEnumerator): CompilerRef? = fqName?.asString()
        ?.let(names::tryEnumerate)
        ?.let(CompilerRef::JavaCompilerClassRef)

    private fun KtConstructor<*>.asCompilerRef(names: NameEnumerator): CompilerRef? = getContainingClassOrObject().fqName
        ?.asString()
        ?.let { qualifier ->
            CompilerRef.JavaCompilerMethodRef(
                names.tryEnumerate(qualifier),
                names.tryEnumerate("<init>"),
                valueParameters.size,
            )
        }

    private fun KtNamedFunction.asCompilerRef(qualifier: String, names: NameEnumerator): CompilerRef = CompilerRef.JavaCompilerMethodRef(
        names.tryEnumerate(qualifier),
        names.tryEnumerate(jvmName ?: name),
        numberOfArguments(countReceiver = true),
    )

    private fun KtProperty.asCompilerRef(qualifier: String, names: NameEnumerator): List<CompilerRef>? = name?.let { propertyName ->
        if (hasModifier(KtTokens.CONST_KEYWORD)) return@let listOf(
            CompilerRef.JavaCompilerFieldRef(
                names.tryEnumerate(qualifier),
                names.tryEnumerate(propertyName),
            )
        )

        val getter = CompilerRef.JavaCompilerMethodRef(
            names.tryEnumerate(qualifier),
            names.tryEnumerate(JvmAbi.getterName(propertyName)),
            numberOfArguments(countReceiver = true),
        )

        val setter = if (isVar)
            CompilerRef.JavaCompilerMethodRef(
                names.tryEnumerate(qualifier),
                names.tryEnumerate(JvmAbi.setterName(propertyName)),
                numberOfArguments(countReceiver = true) + 1,
            )
        else
            null

        listOfNotNull(getter, setter)
    }

    override fun getHierarchyRestrictedToLibraryScope(
        baseRef: CompilerRef,
        basePsi: PsiElement,
        names: NameEnumerator,
        libraryScope: GlobalSearchScope
    ): List<CompilerRef> {
        val overridden = mutableListOf<CompilerRef>()
        val processor = Processor { psiClass: PsiClass ->
            psiClass.takeUnless { it.hasModifierProperty(PsiModifier.PRIVATE) }
                ?.let { runReadAction { it.qualifiedName } }
                ?.let { names.tryEnumerate(it) }
                ?.let { overridden.add(baseRef.override(it)) }

            true
        }

        HierarchySearchRequest(
            originalElement = basePsi,
            searchScope = libraryScope,
            searchDeeply = true,
        ).searchInheritors().forEach(processor)

        return overridden
    }
}
