// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionData
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.KotlinFirExtractFunctionHandler
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractExtractionTest
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.EXTRACT_FUNCTION
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.processDuplicates
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractK2IntroduceFunctionTest : AbstractExtractionTest() {

    override fun getExtractFunctionHandler(
        explicitPreviousSibling: PsiElement?,
        expectedNames: List<String>,
        expectedReturnTypes: List<String>,
        expectedDescriptors: String,
        expectedTypes: String,
        acceptAllScopes: Boolean,
        extractionOptions: ExtractionOptions
    ): AbstractExtractKotlinFunctionHandler {
        return KotlinFirExtractFunctionHandler(
            acceptAllScopes,
            helper = object : ExtractionEngineHelper(EXTRACT_FUNCTION) {
                override fun adjustExtractionData(data: ExtractionData): ExtractionData {
                    return ActionUtil.underModalProgress(project, "adjust for tests") {
                        data.copy(options = extractionOptions)
                    }
                }

                override fun configureAndRun(
                    project: Project,
                    editor: Editor,
                    descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
                    onFinish: (ExtractionResult) -> Unit
                ) {
                    fun afterFinish(extraction: ExtractionResult) {
                        processDuplicates(extraction.duplicateReplacers, project, editor)
                        onFinish(extraction)
                    }
                    doRefactor(ExtractionGeneratorConfiguration(descriptorWithConflicts.descriptor, ExtractionGeneratorOptions.DEFAULT), ::afterFinish)
                }
            }
        )
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