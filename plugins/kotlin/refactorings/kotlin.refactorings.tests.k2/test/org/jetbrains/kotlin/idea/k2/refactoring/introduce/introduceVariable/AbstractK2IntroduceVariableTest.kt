// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractExtractionTest
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionOptions
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractK2IntroduceVariableTest : AbstractExtractionTest() {

    override fun getIntroduceVariableHandler(): RefactoringActionHandler = K2IntroduceVariableHandler

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun getExtractFunctionHandler(
        explicitPreviousSibling: PsiElement?,
        expectedNames: List<String>,
        expectedReturnTypes: List<String>,
        expectedDescriptors: String,
        expectedTypes: String,
        acceptAllScopes: Boolean,
        extractionOptions: ExtractionOptions
    ): AbstractExtractKotlinFunctionHandler {
        throw UnsupportedOperationException()
    }

    override fun doExtractSuperTest(unused: String, isInterface: Boolean) {
        throw UnsupportedOperationException()
    }

    override fun doIntroducePropertyTest(unused: String) {
        throw UnsupportedOperationException()
    }

    override fun getIntroduceTypeAliasHandler(
        explicitPreviousSibling: PsiElement?,
        aliasName: String?,
        aliasVisibility: KtModifierKeywordToken?
    ): RefactoringActionHandler {
        throw UnsupportedOperationException()
    }

    override fun doIntroduceConstantTest(unused: String) {
        throw UnsupportedOperationException()
    }

    override fun updateScriptDependenciesSynchronously(psiFile: PsiFile) {
        // not applicable
    }
}