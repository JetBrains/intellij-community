// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.SmartList
import org.jetbrains.kotlin.idea.base.psi.dropCurlyBracketsIfPossible
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.selectElement
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.findElements
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import kotlin.math.min

fun KtExpression.removeTemplateEntryBracesIfPossible(): KtExpression {
    val parent = parent as? KtBlockStringTemplateEntry ?: return this
    return parent.dropCurlyBracketsIfPossible().expression!!
}

fun KtExpression.mustBeParenthesizedInInitializerPosition(): Boolean {
    if (this !is KtBinaryExpression) return false

    if (left?.mustBeParenthesizedInInitializerPosition() == true) return true
    return PsiChildRange(left, operationReference).any { (it is PsiWhiteSpace) && it.textContains('\n') }
}

fun PsiElement.findExpressionByCopyableDataAndClearIt(key: Key<Boolean>): KtExpression? {
    val result = findDescendantOfType<KtExpression> { it.getCopyableUserData(key) != null } ?: return null
    result.putCopyableUserData(key, null)
    return result
}

fun PsiElement.findElementByCopyableDataAndClearIt(key: Key<Boolean>): PsiElement? {
    val result = findDescendantOfType<PsiElement> { it.getCopyableUserData(key) != null } ?: return null
    result.putCopyableUserData(key, null)
    return result
}

fun PsiElement.findExpressionsByCopyableDataAndClearIt(key: Key<Boolean>): List<KtExpression> {
    val results = collectDescendantsOfType<KtExpression> { it.getCopyableUserData(key) != null }
    results.forEach { it.putCopyableUserData(key, null) }
    return results
}

fun ExtractableSubstringInfo.replaceWith(replacement: KtExpression): KtExpression {
    val psiFactory = KtPsiFactory(replacement.project)
    val parent = startEntry.parent

    psiFactory.createStringTemplate(prefix).entries.singleOrNull()?.let { parent.addBefore(it, startEntry) }

    val refEntry = createStringTemplateEntryFromExpression(replacement, psiFactory)
    val addedRefEntry = parent.addBefore(refEntry, startEntry) as KtStringTemplateEntryWithExpression

    psiFactory.createStringTemplate(suffix).entries.singleOrNull()?.let { parent.addAfter(it, endEntry) }

    parent.deleteChildRange(startEntry, endEntry)

    return addedRefEntry.expression!!
}

private fun ExtractableSubstringInfo.createStringTemplateEntryFromExpression(
    replacement: KtExpression,
    psiFactory: KtPsiFactory
): KtStringTemplateEntryWithExpression {
    val interpolationPrefix = template.interpolationPrefix
    return if (interpolationPrefix != null) {
        psiFactory.createMultiDollarBlockStringTemplateEntry(replacement, prefixLength = interpolationPrefix.textLength)
    } else {
        psiFactory.createBlockStringTemplateEntry(replacement)
    }
}

fun findStringTemplateOrStringTemplateEntryExpression(file: KtFile, startOffset: Int, endOffset: Int, kind: ElementKind): KtExpression? {
    if (kind != ElementKind.EXPRESSION) return null

    val startEntry = file.findElementAt(startOffset)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null
    val endEntry = file.findElementAt(endOffset - 1)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null

    if (startEntry == endEntry && startEntry is KtStringTemplateEntryWithExpression) return startEntry.expression

    val stringTemplate = startEntry.parent as? KtStringTemplateExpression ?: return null
    if (endEntry.parent != stringTemplate) return null

    val templateOffset = stringTemplate.startOffset
    if (stringTemplate.getContentRange().equalsToRange(startOffset - templateOffset, endOffset - templateOffset)) return stringTemplate

    return null
}

fun KtExpression.getContainingLambdaOutsideParentheses(): KtLambdaArgument? {
    val parent = parent
    return when (parent) {
        is KtLambdaArgument -> parent
        is KtLabeledExpression -> parent.getContainingLambdaOutsideParentheses()
        else -> null
    }
}

fun calculateAnchorForExpressions(commonParent: PsiElement, commonContainer: PsiElement, expressions: List<KtExpression>): KtElement? {
    if (commonParent != commonContainer) {
        return commonParent.parents(withSelf = true).firstOrNull { it.parent == commonContainer } as? KtElement
    }
    val startOffset = expressions.fold(commonContainer.endOffset) { offset, expression ->
        min(offset, expression.substringContextOrThis.startOffset)
    }

    return commonContainer.allChildren.lastOrNull { it.textRange.contains(startOffset) } as? KtElement
}

fun validateExpressionElements(elements: List<PsiElement>): String? {
    if (elements.any { it is KtConstructor<*> || it is KtParameter || it is KtTypeAlias || it is KtPropertyAccessor || it is KtFunction && !it.isLocal && it.parent?.parent !is KtScript }) {
        return KotlinBundle.message("text.refactoring.is.not.applicable.to.this.code.fragment")
    }
    return null
}

fun selectElementsWithTargetSibling(
    @NlsContexts.DialogTitle operationName: String,
    editor: Editor,
    file: KtFile,
    @NlsContexts.DialogTitle title: String,
    elementKinds: Collection<ElementKind>,
    elementValidator: (List<PsiElement>) -> String?,
    getContainers: (elements: List<PsiElement>, commonParent: PsiElement) -> List<PsiElement>,
    continuation: (elements: List<PsiElement>, targetSibling: PsiElement) -> Unit,
    selection: ((elements: List<PsiElement>, commonParent: PsiElement) -> PsiElement?)? = null
) {
    fun onSelectionComplete(elements: List<PsiElement>, targetContainer: PsiElement) {
        val physicalElements = elements.map { it.substringContextOrThis }
        val parent = PsiTreeUtil.findCommonParent(physicalElements)
            ?: throw AssertionError("Should have at least one parent: ${physicalElements.joinToString("\n")}")

        if (parent == targetContainer) {
            continuation(elements, physicalElements.first())
            return
        }

        val outermostParent = parent.getOutermostParentContainedIn(targetContainer)
        if (outermostParent == null) {
            showErrorHintByKey(file.project, editor, "cannot.refactor.no.container", operationName)
            return
        }

        continuation(elements, outermostParent)
    }

    selectElementsWithTargetParent(
        operationName,
        editor,
        file,
        title,
        elementKinds,
        elementValidator,
        getContainers,
        ::onSelectionComplete,
        selection
    )
}

fun selectElementsWithTargetParent(
    @NlsContexts.DialogTitle operationName: String,
    editor: Editor,
    file: KtFile,
    @NlsContexts.DialogTitle title: String,
    elementKinds: Collection<ElementKind>,
    elementValidator: (List<PsiElement>) -> @NlsContexts.DialogMessage String?,
    getContainers: (List<PsiElement>, PsiElement) -> List<PsiElement>,
    continuation: (List<PsiElement>, PsiElement) -> Unit,
    selection: ((List<PsiElement>, PsiElement) -> PsiElement?)? = null
) {
    fun showErrorHintByKey(key: String) {
        showErrorHintByKey(file.project, editor, key, operationName)
    }

    fun selectTargetContainer(elements: List<PsiElement>) {
        elementValidator(elements)?.let {
            showErrorHint(file.project, editor, it, operationName)
            return
        }

        val physicalElements = elements.map { it.substringContextOrThis }
        val parent = PsiTreeUtil.findCommonParent(physicalElements)
            ?: throw AssertionError("Should have at least one parent: ${physicalElements.joinToString("\n")}")

        val containers = getContainers(physicalElements, parent)
        if (containers.isEmpty()) {
            showErrorHintByKey("cannot.refactor.no.container")
            return
        }

        chooseContainerElementIfNecessary(
            containers,
            editor,
            title,
            true,
            selection?.invoke(physicalElements, parent)
        ) {
            continuation(elements, it)
        }
    }

    fun selectMultipleElements() {
        val startOffset = editor.selectionModel.selectionStart
        val endOffset = editor.selectionModel.selectionEnd

        val elements = elementKinds.flatMap { findElements(file, startOffset, endOffset, it).toList() }
        if (elements.isEmpty()) {
            return when (elementKinds.singleOrNull()) {
                ElementKind.EXPRESSION -> showErrorHintByKey("cannot.refactor.no.expression")
                ElementKind.TYPE_ELEMENT -> showErrorHintByKey("cannot.refactor.no.type")
                else -> showErrorHint(
                    file.project,
                    editor,
                    KotlinBundle.message("text.refactoring.can.t.be.performed.on.the.selected.code.element"),
                    title
                )
            }
        }

        selectTargetContainer(elements)
    }

    fun selectSingleElement() {
        selectElement(editor, file, false, elementKinds) { expr ->
            if (expr != null) {
                selectTargetContainer(listOf(expr))
            } else {
                if (!editor.selectionModel.hasSelection()) {
                    if (elementKinds.singleOrNull() == ElementKind.EXPRESSION) {
                        val elementAtCaret = file.findElementAt(editor.caretModel.offset)
                        elementAtCaret?.getParentOfTypeAndBranch<KtProperty> { nameIdentifier }?.let {
                            return@selectElement selectTargetContainer(listOf(it))
                        }
                    }

                    editor.selectionModel.selectLineAtCaret()
                }
                selectMultipleElements()
            }
        }
    }

    editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    selectSingleElement()
}

fun KotlinPsiRange.getPhysicalTextRange(): TextRange {
    return (elements.singleOrNull() as? KtExpression)?.extractableSubstringInfo?.contentRange ?: textRange
}

fun isObjectOrNonInnerClass(e: PsiElement): Boolean = e is KtObjectDeclaration || (e is KtClass && !e.isInner())

fun <T : KtDeclaration> insertDeclaration(declaration: T, targetSibling: PsiElement): T {
    val targetParent = targetSibling.parent

    val anchorCandidates = SmartList<PsiElement>()
    anchorCandidates.add(targetSibling)
    if (targetSibling is KtEnumEntry) {
        anchorCandidates.add(targetSibling.siblings().last { it is KtEnumEntry })
    }

    val anchor = anchorCandidates.minByOrNull { it.startOffset }!!.parentsWithSelf.first { it.parent == targetParent }
    val targetContainer = anchor.parent!!

    @Suppress("UNCHECKED_CAST")
    return (targetContainer.addBefore(declaration, anchor) as T).apply {
        targetContainer.addBefore(KtPsiFactory(declaration.project).createWhiteSpace("\n\n"), anchor)
    }
}


fun showErrorHint(project: Project, editor: Editor, @NlsContexts.DialogMessage message: String, @NlsContexts.DialogTitle title: String) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw CommonRefactoringUtil.RefactoringErrorHintException(message);
    CommonRefactoringUtil.showErrorHint(project, editor, message, title, null);
}

fun showErrorHintByKey(project: Project, editor: Editor, messageKey: String, @NlsContexts.DialogTitle title: String) {
    showErrorHint(project, editor, KotlinBundle.message(messageKey), title)
}

fun KtNamedDeclaration.getGeneratedBody(): KtExpression =
    when (this) {
        is KtNamedFunction -> bodyExpression
        else -> {
            val property = this as KtProperty

            property.getter?.bodyExpression?.let { return it }
            property.initializer?.let { return it }
            // We assume lazy property here with delegate expression 'by Delegates.lazy { body }'
            property.delegateExpression?.let {
                val call = it.getCalleeExpressionIfAny()?.parent as? KtCallExpression
                call?.lambdaArguments?.singleOrNull()?.getLambdaExpression()?.bodyExpression
            }
        }
    } ?: throw AssertionError("Couldn't get block body for this declaration: ${getElementTextWithContext()}")
