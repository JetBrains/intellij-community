// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.introduce.IntroduceRefactoringException
import org.jetbrains.kotlin.idea.refactoring.renderTrimmed
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.findElement
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import kotlin.math.min

internal fun selectElement(
    editor: Editor,
    file: KtFile,
    elementKind: ElementKind,
    callback: (PsiElement?) -> Unit
) = selectElement(editor, file, true, listOf(elementKind), callback)

private fun selectElement(
    editor: Editor,
    file: KtFile,
    failOnEmptySuggestion: Boolean,
    elementKinds: Collection<ElementKind>,
    callback: (PsiElement?) -> Unit
) {
    if (editor.selectionModel.hasSelection()) {
        val selectionStart = editor.selectionModel.selectionStart
        val selectionEnd = editor.selectionModel.selectionEnd
        val element = findElementAtRange(file, selectionStart, selectionEnd, elementKinds, failOnEmptySuggestion)
        callback(element)
    } else {
        val offset = editor.caretModel.offset
        smartSelectElement(editor, file, offset, failOnEmptySuggestion, elementKinds, callback)
    }
}

internal fun findElementAtRange(
    file: KtFile,
    selectionStart: Int,
    selectionEnd: Int,
    elementKinds: Collection<ElementKind>,
    failOnEmptySuggestion: Boolean
): PsiElement? {
    var adjustedStart = selectionStart
    var adjustedEnd = selectionEnd
    var firstElement: PsiElement = file.findElementAt(adjustedStart)!!
    var lastElement: PsiElement = file.findElementAt(adjustedEnd - 1)!!

    if (PsiTreeUtil.getParentOfType(
            firstElement,
            KtLiteralStringTemplateEntry::class.java,
            KtEscapeStringTemplateEntry::class.java
        ) == null
        && PsiTreeUtil.getParentOfType(
            lastElement,
            KtLiteralStringTemplateEntry::class.java,
            KtEscapeStringTemplateEntry::class.java
        ) == null
    ) {
        firstElement = firstElement.getNextSiblingIgnoringWhitespaceAndComments(true)!!
        lastElement = lastElement.getPrevSiblingIgnoringWhitespaceAndComments(true)!!
        adjustedStart = firstElement.textRange.startOffset
        adjustedEnd = lastElement.textRange.endOffset
    }

    return elementKinds.asSequence()
        .mapNotNull { findElement(file, adjustedStart, adjustedEnd, failOnEmptySuggestion, it) }
        .firstOrNull()
}

internal fun getSmartSelectSuggestions(
    file: PsiFile,
    offset: Int,
    elementKind: ElementKind,
    isOriginalOffset: Boolean = true,
): List<KtElement> {
    if (offset < 0) return emptyList()

    var element: PsiElement? = file.findElementAt(offset) ?: return emptyList()

    if (element is PsiWhiteSpace
        || isOriginalOffset && element?.node?.elementType == KtTokens.RPAR
        || element is PsiComment
        || element?.getStrictParentOfType<KDoc>() != null
    ) return getSmartSelectSuggestions(file, offset - 1, elementKind, isOriginalOffset = false)

    val elements = ArrayList<KtElement>()
    while (element != null && !(element is KtBlockExpression && element.parent !is KtFunctionLiteral) &&
        element !is KtNamedFunction
        && element !is KtClassBody
    ) {
        var addElement = false
        var keepPrevious = true

        if (element is KtTypeElement) {
            addElement =
                elementKind == ElementKind.TYPE_ELEMENT
                        && element.getParentOfTypeAndBranch<KtUserType>(true) { qualifier } == null
            if (!addElement) {
                keepPrevious = false
            }
        } else if (element is KtExpression && element !is KtStatementExpression) {
            addElement = elementKind == ElementKind.EXPRESSION

            if (addElement) {
                if (element is KtParenthesizedExpression) {
                    addElement = false
                } else if (KtPsiUtil.isLabelIdentifierExpression(element)) {
                    addElement = false
                } else if (element.parent is KtQualifiedExpression) {
                    val qualifiedExpression = element.parent as KtQualifiedExpression
                    if (qualifiedExpression.receiverExpression !== element) {
                        addElement = false
                    }
                } else if (element.parent is KtCallElement
                    || element.parent is KtThisExpression
                    || PsiTreeUtil.getParentOfType(element, KtSuperExpression::class.java) != null
                ) {
                    addElement = false
                } else if (element.parent is KtOperationExpression) {
                    val operationExpression = element.parent as KtOperationExpression
                    if (operationExpression.operationReference === element) {
                        addElement = false
                    }
                }
                if (addElement) {
                    if (isUnitTypeOrNull(element)) {
                        addElement = false
                    }
                }
            }
        }

        if (addElement) {
            elements.add(element as KtElement)
        }

        if (!keepPrevious) {
            elements.clear()
        }

        element = element.parent
    }
    return elements
}

private fun isUnitTypeOrNull(element: KtExpression): Boolean {
    return analyzeInModalWindow(element, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
        val expressionType = element.getKtType()
        expressionType == null || expressionType.isUnit
    }
}

private fun smartSelectElement(
    editor: Editor,
    file: PsiFile,
    offset: Int,
    failOnEmptySuggestion: Boolean,
    elementKinds: Collection<ElementKind>,
    callback: (PsiElement?) -> Unit
) {
    val elements = elementKinds.flatMap { getSmartSelectSuggestions(file, offset, it) }
    if (elements.isEmpty()) {
        if (failOnEmptySuggestion) throw IntroduceRefactoringException(
            KotlinBundle.message("cannot.refactor.not.expression")
        )
        callback(null)
        return
    }

    if (elements.size == 1 || isUnitTestMode()) {
        callback(elements.first())
        return
    }

    val highlighter = ScopeHighlighter(editor)
    val title = if (elementKinds.size == 1) {
        when (elementKinds.iterator().next()) {
            ElementKind.EXPRESSION -> KotlinBundle.message("popup.title.expressions")
            ElementKind.TYPE_ELEMENT, ElementKind.TYPE_CONSTRUCTOR -> KotlinBundle.message("popup.title.types")
        }
    } else {
        KotlinBundle.message("popup.title.elements")
    }

    PsiTargetNavigator(elements)
        .presentationProvider { element -> TargetPresentation.builder(getExpressionShortText(element)).presentation() }
        .builderConsumer { builder ->
            builder
                .setMovable(false)
                .setResizable(false)
                .setRequestFocus(true)
                .setItemChosenCallback { presentation -> callback((presentation.item as SmartPsiElementPointer<*>).element) }
                .setItemSelectedCallback { presentation ->
                    highlighter.dropHighlight()
                    val psiElement = (presentation?.item as? SmartPsiElementPointer<*>)?.element ?: return@setItemSelectedCallback
                    highlighter.highlight(psiElement, listOf(psiElement))
                }
                .addListener(object : JBPopupListener {
                    override fun onClosed(event: LightweightWindowEvent) {
                        highlighter.dropHighlight()
                    }
                })
        }
        .createPopup(file.project, title)
        .showInBestPositionFor(editor)
}

@NlsSafe
private fun getExpressionShortText(element: KtElement): String {
    val text = element.renderTrimmed().trimStart()
    val firstNewLinePos = text.indexOf('\n')
    var trimmedText = text.substring(0, if (firstNewLinePos != -1) firstNewLinePos else min(100, text.length))
    if (trimmedText.length != text.length) trimmedText += " ..."
    return trimmedText
}

private fun findElement(
    file: KtFile,
    startOffset: Int,
    endOffset: Int,
    failOnNoExpression: Boolean,
    elementKind: ElementKind
): PsiElement? {
    val element = findElement(file, startOffset, endOffset, elementKind)

    if (element == null) {
        //todo: if it's infix expression => add (), then commit document then return new created expression

        if (failOnNoExpression) {
            throw IntroduceRefactoringException(KotlinBundle.message("cannot.refactor.not.expression"))
        }
        return null
    }

    return element
}