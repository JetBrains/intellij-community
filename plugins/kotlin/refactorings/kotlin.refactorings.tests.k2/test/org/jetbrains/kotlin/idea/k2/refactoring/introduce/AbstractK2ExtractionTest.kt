// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.util.DocCommentPolicy
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.chooseMembers
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractExtractionTest
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ExtractSuperInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperConflictSearcher
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperRefactoring
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionOptions
import org.jetbrains.kotlin.idea.refactoring.markMembersInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.extractClassMembers
import org.jetbrains.kotlin.idea.test.util.findElementByCommentPrefix
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

abstract class AbstractK2ExtractionTest : AbstractExtractionTest() {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun doExtractSuperTest(unused: String, isInterface: Boolean) {
        doTest(checkAdditionalAfterdata = true) { file ->
            markMembersInfo(file)

            val targetParent = file.findElementByCommentPrefix("// SIBLING:")?.parent ?: file.parent!!
            val fileText = file.text
            val className = InTextDirectivesUtils.stringWithDirective(fileText, "NAME")
            val targetFileName = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// TARGET_FILE_NAME:")
                ?: "$className.${KotlinFileType.EXTENSION}"
            val editor = fixture.editor
            val originalClass = file.findElementAt(editor.caretModel.offset)?.getStrictParentOfType<KtClassOrObject>()!!
            val memberInfos = allowAnalysisOnEdt { chooseMembers(extractClassMembers(originalClass)) }
            val conflicts = KotlinExtractSuperConflictSearcher.getInstance().collectConflicts(
                originalClass,
                memberInfos,
                targetParent,
                className,
                isInterface,
            )
            project.checkConflictsInteractively(conflicts) {
                val extractInfo = ExtractSuperInfo(
                    originalClass,
                    memberInfos,
                    targetParent,
                    targetFileName,
                    className,
                    isInterface,
                    DocCommentPolicy(DocCommentPolicy.ASIS)
                )
                KotlinExtractSuperRefactoring.getInstance().performRefactoring(extractInfo)
            }
        }
    }

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
