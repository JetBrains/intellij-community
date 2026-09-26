// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.k2.refactoring.introduceTypeAlias.IntroduceTypeAliasDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.introduceTypeAlias.KotlinIntroduceTypeAliasHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractExtractionTest
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionOptions
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractK2IntroduceTypeAliasTest : AbstractExtractionTest() {

    override fun getIntroduceTypeAliasHandler(
        explicitPreviousSibling: PsiElement?,
        aliasName: String?,
        aliasVisibility: KtModifierKeywordToken?
    ): RefactoringActionHandler {
        return object : KotlinIntroduceTypeAliasHandler() {
            override fun doInvoke(
                project: Project,
                editor: Editor,
                elements: List<PsiElement>,
                targetSibling: KtElement,
                descriptorSubstitutor: ((IntroduceTypeAliasDescriptor) -> IntroduceTypeAliasDescriptor)?
            ) {
                super.doInvoke(project, editor, elements, (explicitPreviousSibling ?: targetSibling) as KtElement) {
                    it.copy(name = aliasName ?: it.name, visibility = aliasVisibility ?: it.visibility)
                }
            }
        }
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun getIntroduceVariableHandler(): RefactoringActionHandler {
        throw UnsupportedOperationException()
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

    override fun doIntroduceConstantTest(unused: String) {
        throw UnsupportedOperationException()
    }

    override fun updateScriptDependenciesSynchronously(psiFile: PsiFile) {
        // not applicable
    }
}
