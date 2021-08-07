// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase
import com.intellij.java.compiler.CompilerReferencesTestBase
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.runAll
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.utils.addToStdlib.cast
import kotlin.properties.Delegates

abstract class KotlinCompilerReferenceTestBase : CompilerReferencesTestBase() {
    private var defaultEnableState by Delegates.notNull<Boolean>()

    override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
        moduleBuilder.addLibrary(KotlinArtifactNames.KOTLIN_STDLIB, KotlinArtifacts.instance.kotlinStdlib.path)
    }

    override fun setUp() {
        super.setUp()
        defaultEnableState = AdvancedSettings.getBoolean(KotlinCompilerReferenceIndexService.SETTINGS_ID)
        AdvancedSettings.setBoolean(KotlinCompilerReferenceIndexService.SETTINGS_ID, true)
    }

    override fun tearDown() = runAll(
        { AdvancedSettings.setBoolean(KotlinCompilerReferenceIndexService.SETTINGS_ID, defaultEnableState) },
        { super.tearDown() },
    )

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
}