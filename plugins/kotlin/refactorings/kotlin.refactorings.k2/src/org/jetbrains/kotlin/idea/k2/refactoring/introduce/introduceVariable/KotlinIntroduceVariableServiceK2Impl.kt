// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableHandler.getCandidateContainers
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.introduce.IntroduceRefactoringException
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableService
import org.jetbrains.kotlin.idea.refactoring.introduce.findStringTemplateOrStringTemplateEntryExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.findElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal class KotlinIntroduceVariableServiceK2Impl(private val project: Project) : KotlinIntroduceVariableService {
    override fun findElement(
        file: KtFile,
        startOffset: Int,
        endOffset: Int,
        failOnNoExpression: Boolean,
        elementKind: ElementKind
    ): PsiElement? {
        var element = findElement(file, startOffset, endOffset, elementKind)
            ?: findStringTemplateOrStringTemplateEntryExpression(file, startOffset, endOffset, elementKind)
            ?: findStringTemplateFragment(file, startOffset, endOffset, elementKind)

        if (element is KtElement && isNonExtractableQualifier(element)) {
            element = null
        }

        if (element == null) {
            if (failOnNoExpression) {
                throw IntroduceRefactoringException(KotlinBundle.message("cannot.refactor.not.expression"))
            }
            return null
        }

        return element
    }

    override fun getContainersForExpression(expression: KtExpression): List<KotlinIntroduceVariableHelper.Containers> {
        return expression.getCandidateContainers()
    }

    override fun findOccurrences(
        expression: KtExpression,
        occurrenceContainer: KtElement,
    ): List<KtExpression> = with(K2IntroduceVariableHandler) {
        expression.findOccurrences(occurrenceContainer)
    }

    override fun doRefactoringWithContainer(
        editor: Editor?,
        expressionToExtract: KtExpression,
        container: KtElement,
        occurrencesToReplace: List<KtExpression>?
    ) {
        K2IntroduceVariableHandler.doRefactoringWithSelectedTargetContainer(
            project = project,
            editor = editor,
            expression = expressionToExtract,
            // TODO: fix occurence container (currently it is not used in K2-implementation)
            containers = KotlinIntroduceVariableHelper.Containers(container, container),
            isVar = KotlinCommonRefactoringSettings.getInstance().INTRODUCE_DECLARE_WITH_VAR,
        )
    }

    override fun hasUnitType(element: KtExpression): Boolean {
        return analyzeInModalWindow(element, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            val expressionType = element.expressionType
            expressionType == null || expressionType.isUnitType
        } || isNonExtractableQualifier(element)
    }

    private fun isNonExtractableQualifier(element: KtElement): Boolean {
        val isQualifier = when (val parent = element.parent) {
            is KtDotQualifiedExpression -> parent.receiverExpression == element
            is KtDoubleColonExpression -> parent.receiverExpression == element
            else -> false
        }
        if (!isQualifier) return false

        val resolved = ((element as? KtDotQualifiedExpression)?.selectorExpression ?: element).mainReference?.resolve()

        return resolved is PsiPackage ||
                resolved is PsiClass ||
                resolved is KtTypeAlias ||
                resolved is KtClassOrObject && resolved.getDeclarationKeyword()?.elementType != KtTokens.OBJECT_KEYWORD
    }
}