// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.java.codeInsight.completion.AbstractCompilerAwareTest
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.runAll
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.test.KotlinRoot
import kotlin.properties.Delegates

abstract class KotlinCompilerReferenceTestBase : AbstractCompilerAwareTest() {
    private var defaultEnableState by Delegates.notNull<Boolean>()

    protected fun getTestDataPath(testDirectory: String): String = KotlinRoot.DIR
        .resolve("refIndex/tests/testData/")
        .resolve(testDirectory)
        .path + "/"

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
        val declarationAtCaret = elementAtCaret.parentOfType<KtNamedDeclaration>(withSelf = true) ?: error("declaration at caret not found")
        return getReferentFiles(declarationAtCaret)
    }

    protected fun getReferentFiles(element: PsiElement): Set<String>? = KotlinCompilerReferenceIndexService[project]
        .findReferenceFilesInTests(element)
        ?.mapTo(mutableSetOf(), VirtualFile::getName)
}