// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.analysis.api.fir.symbols.KtPackage
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableHandler.getCandidateContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.IntroduceRefactoringException
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableService
import org.jetbrains.kotlin.idea.refactoring.introduce.findStringTemplateOrStringTemplateEntryExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.findElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeAlias

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

        if (element is KtExpression) {
            val qualifiedExpression = element.parent as? KtDotQualifiedExpression
            if (qualifiedExpression != null && qualifiedExpression.receiverExpression == element) {
                val resolved = ((element as? KtDotQualifiedExpression)?.selectorExpression ?: element).mainReference?.resolve()
                if (resolved is PsiPackage || resolved is PsiClass ||
                    resolved is KtTypeAlias || resolved is KtClassOrObject && resolved.getDeclarationKeyword()?.elementType != KtTokens.OBJECT_KEYWORD) {
                    element = null
                }
            }
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
            project, editor,
            expressionToExtract,
            // TODO: fix occurence container (currently it is not used in K2-implementation)
            KotlinIntroduceVariableHelper.Containers(container, container),
            isVar = false,
        )
    }

    override fun hasUnitType(element: KtExpression): Boolean {
        return analyzeInModalWindow(element, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            val expressionType = element.getKtType()
            expressionType == null || expressionType.isUnit
        }
    }
}