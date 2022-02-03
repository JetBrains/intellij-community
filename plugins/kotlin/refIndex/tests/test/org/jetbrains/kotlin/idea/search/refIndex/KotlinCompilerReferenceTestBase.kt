// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase
import com.intellij.java.compiler.CompilerReferencesTestBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.cast

abstract class KotlinCompilerReferenceTestBase : CompilerReferencesTestBase() {
    override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
        super.tuneFixture(moduleBuilder)
        moduleBuilder.addLibrary(KotlinArtifactNames.KOTLIN_STDLIB, KotlinArtifacts.instance.kotlinStdlib.path)
    }

    override fun setUp() {
        super.setUp()
        KotlinCompilerReferenceIndexService[project]
    }

    protected fun getReferentFilesForElementUnderCaret(): Set<String>? {
        val elementAtCaret = myFixture.elementAtCaret
        val declarationAtCaret = elementAtCaret.parentOfType<PsiNamedElement>(withSelf = true) ?: error("declaration at caret not found")
        return getReferentFiles(declarationAtCaret, true)
    }

    protected fun getReferentFiles(element: PsiElement, withJavaIndex: Boolean): Set<String>? {
        val fromKotlinIndex = KotlinCompilerReferenceIndexService[project].findReferenceFilesInTests(element)
        val fromJavaIndex = CompilerReferenceService.getInstance(project)
            .takeIf { withJavaIndex }
            ?.cast<CompilerReferenceServiceBase<*>>()
            ?.getReferentFilesForTests(element)

        if (fromKotlinIndex == null && fromJavaIndex == null) return null
        return mutableSetOf<String>().apply {
            fromKotlinIndex?.mapTo(this, VirtualFile::getName)
            fromJavaIndex?.mapTo(this, VirtualFile::getName)
        }
    }

    protected fun findSubOrSuperTypes(name: String, deep: Boolean, subtypes: Boolean): List<String>? {
        val service = KotlinCompilerReferenceIndexService[project]
        val fqName = FqName(name)
        val sequence = if (subtypes)
            service.getSubtypesOfInTests(fqName, deep)
        else
            throw NotImplementedError("supertypes not supported")

        return sequence?.map { it.asString() }?.sorted()?.toList()
    }

    protected fun findHierarchy(hierarchyElement: PsiElement): List<String> = KotlinCompilerReferenceIndexService[project]
        .getSubtypesOfInTests(hierarchyElement)
        .map(FqName::asString)
        .sorted()
        .toList()

    protected fun forEachBoolean(action: (Boolean) -> Unit) = listOf(true, false).forEach(action)
}