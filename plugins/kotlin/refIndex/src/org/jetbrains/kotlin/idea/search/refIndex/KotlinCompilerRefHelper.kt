// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.backwardRefs.LanguageCompilerRefAdapter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.roots.impl.LibraryScopeCache
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.NameEnumerator
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.psi.KtClassOrObject

class KotlinCompilerRefHelper : LanguageCompilerRefAdapter.ExternalLanguageHelper() {
    override fun getAffectedFileTypes(): Set<FileType> = setOf(KotlinFileType.INSTANCE)

    override fun asCompilerRef(element: PsiElement, names: NameEnumerator): CompilerRef? = when (element) {
        is KtClassOrObject -> element.fqName
            ?.asString()
            ?.tryEnumerate(names)
            ?.let(CompilerRef::JavaCompilerClassRef)

        else -> null
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
                ?.tryEnumerate(names)
                ?.let { overridden.add(baseRef.override(it)) }

            true
        }

        HierarchySearchRequest(
            originalElement = basePsi,
            searchScope = LibraryScopeCache.getInstance(basePsi.project).librariesOnlyScope,
            searchDeeply = true,
        ).searchInheritors().forEach(processor)

        return overridden
    }
}

private fun String.tryEnumerate(names: NameEnumerator): Int? = names.tryEnumerate(this).takeUnless { it == 0 }
