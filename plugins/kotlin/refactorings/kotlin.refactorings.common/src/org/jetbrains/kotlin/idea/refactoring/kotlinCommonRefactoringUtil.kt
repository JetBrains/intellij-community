// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.suggested.endOffset
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.siblings
import java.util.Collections
import kotlin.math.min

fun KtFile.createTempCopy(text: String? = null): KtFile {
    val tmpFile = KtPsiFactory.contextual(this).createFile(name, text ?: this.text ?: "")
    tmpFile.originalFile = this
    return tmpFile
}

fun PsiElement.canRefactorElement(): Boolean {
  return when {
    !isValid -> false
    this is PsiPackage ->
      directories.any { it.canRefactorElement() }
    this is KtElement || (this is PsiMember && language == JavaLanguage.INSTANCE) || this is PsiDirectory ->
      RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true).matches(this)
    else -> false
  }
}

fun KtClass.isOpen(): Boolean = hasModifier(KtTokens.OPEN_KEYWORD) || this.isAbstract() || this.isInterfaceClass() || this.isSealed()

fun PsiElement.isInterfaceClass(): Boolean = when (this) {
    is KtClass -> isInterface()
    is PsiClass -> isInterface
    is KtPsiClassWrapper -> psiClass.isInterface
    else -> false
}

fun KtDeclaration.isAbstract(): Boolean = when {
    hasModifier(KtTokens.ABSTRACT_KEYWORD) -> true
    containingClassOrObject?.isInterfaceClass() != true -> false
    this is KtProperty -> initializer == null && delegate == null && accessors.isEmpty()
    this is KtNamedFunction -> !hasBody()
    else -> false
}

fun KtCallExpression.getLastLambdaExpression(): KtLambdaExpression? {
    if (lambdaArguments.isNotEmpty()) return null
    return valueArguments.lastOrNull()?.getArgumentExpression()?.unpackFunctionLiteral()
}

fun KtCallExpression.isComplexCallWithLambdaArgument(): Boolean = when {
    valueArguments.lastOrNull()?.isNamed() == true -> true
    valueArguments.count { it.getArgumentExpression()?.unpackFunctionLiteral() != null } > 1 -> true
    else -> false
}

fun KtCallExpression.moveFunctionLiteralOutsideParentheses(moveCaretTo: ((Int) -> Unit)? = null) {
    assert(lambdaArguments.isEmpty())
    val argumentList = valueArgumentList!!
    val arguments = argumentList.arguments
    val argument = arguments.last()
    val expression = argument.getArgumentExpression()!!
    assert(expression.unpackFunctionLiteral() != null)

    fun isWhiteSpaceOrComment(e: PsiElement) = e is PsiWhiteSpace || e is PsiComment

    val prevComma = argument.siblings(forward = false, withItself = false).firstOrNull { it.elementType == KtTokens.COMMA }
    val prevComments = (prevComma ?: argumentList.leftParenthesis)
        ?.siblings(forward = true, withItself = false)
        ?.takeWhile(::isWhiteSpaceOrComment)?.toList().orEmpty()
    val nextComments = argumentList.rightParenthesis
        ?.siblings(forward = false, withItself = false)
        ?.takeWhile(::isWhiteSpaceOrComment)?.toList()?.reversed().orEmpty()

    val psiFactory = KtPsiFactory(project)
    val dummyCall = psiFactory.createExpression("foo() {}") as KtCallExpression
    val functionLiteralArgument = dummyCall.lambdaArguments.single()
    functionLiteralArgument.getArgumentExpression()?.replace(expression)

    if (prevComments.any { it is PsiComment }) {
        if (prevComments.firstOrNull() !is PsiWhiteSpace) this.add(psiFactory.createWhiteSpace())
        prevComments.forEach { this.add(it) }
        prevComments.forEach { if (it is PsiComment) it.delete() }
    }
    this.add(functionLiteralArgument)
    if (nextComments.any { it is PsiComment }) {
        nextComments.forEach { this.add(it) }
        nextComments.forEach { if (it is PsiComment) it.delete() }
    }

    /* we should not remove empty parenthesis when callee is a call too - it won't parse */
    if (argumentList.arguments.size == 1 && calleeExpression !is KtCallExpression) {
        argumentList.delete()
        calleeExpression?.let { moveCaretTo?.invoke(it.endOffset) }
    } else {
        argumentList.removeArgument(argument)
        if (arguments.size > 1) {
            arguments[arguments.size - 2]?.let { moveCaretTo?.invoke(it.endOffset) }
        }
    }
}
fun <ListType : KtElement> replaceListPsiAndKeepDelimiters(
    changeInfo: ChangeInfo,
    originalList: ListType,
    newList: ListType,
    @Suppress("UNCHECKED_CAST") listReplacer: ListType.(ListType) -> ListType = { replace(it) as ListType },
    itemsFun: ListType.() -> List<KtElement>
): ListType {
    originalList.children.takeWhile { it is PsiErrorElement }.forEach { it.delete() }

    val oldParameters = originalList.itemsFun().toMutableList()
    val newParameters = newList.itemsFun()
    val oldCount = oldParameters.size
    val newCount = newParameters.size

    val commonCount = min(oldCount, newCount)
    val originalIndexes = changeInfo.newParameters.map { it.oldIndex }
    val keepComments = originalList.allChildren.any { it is PsiComment } &&
            oldCount > commonCount && originalIndexes == originalIndexes.sorted()
    if (!keepComments) {
        for (i in 0 until commonCount) {
            oldParameters[i] = oldParameters[i].replace(newParameters[i]) as KtElement
        }
    }

    if (commonCount == 0 && !keepComments) return originalList.listReplacer(newList)

    if (oldCount > commonCount) {
        if (keepComments) {
            ((0 until oldParameters.size) - originalIndexes).forEach { index ->
                val oldParameter = oldParameters[index]
                val nextComma = oldParameter.getNextSiblingIgnoringWhitespaceAndComments()?.takeIf { it.node.elementType == KtTokens.COMMA }
                if (nextComma != null) {
                    nextComma.delete()
                } else {
                    oldParameter.getPrevSiblingIgnoringWhitespaceAndComments()?.takeIf { it.node.elementType == KtTokens.COMMA }?.delete()
                }
                oldParameter.delete()
            }
        } else {
            originalList.deleteChildRange(oldParameters[commonCount - 1].nextSibling, oldParameters.last())
        }
    } else if (newCount > commonCount) {
        val lastOriginalParameter = oldParameters.last()
        val psiBeforeLastParameter = lastOriginalParameter.prevSibling
        val withMultiline =
            (psiBeforeLastParameter is PsiWhiteSpace || psiBeforeLastParameter is PsiComment) && psiBeforeLastParameter.textContains('\n')
        val extraSpace = if (withMultiline) KtPsiFactory(originalList.project).createNewLine() else null
        originalList.addRangeAfter(newParameters[commonCount - 1].nextSibling, newParameters.last(), lastOriginalParameter)
        if (extraSpace != null) {
            val addedItems = originalList.itemsFun().subList(commonCount, newCount)
            for (addedItem in addedItems) {
                val elementBefore = addedItem.prevSibling
                if ((elementBefore !is PsiWhiteSpace && elementBefore !is PsiComment) || !elementBefore.textContains('\n')) {
                    addedItem.parent.addBefore(extraSpace, addedItem)
                }
            }
        }
    }

    return originalList
}

fun KtNamedDeclaration.getDeclarationBody(): KtElement? = when (this) {
    is KtClassOrObject -> getSuperTypeList()
    is KtPrimaryConstructor -> getContainingClassOrObject().getSuperTypeList()
    is KtSecondaryConstructor -> getDelegationCall()
    is KtNamedFunction -> bodyExpression
    else -> null
}

fun KtElement.isInsideOfCallerBody(
    allUsages: Array<out UsageInfo>,
    isCaller: PsiElement.(Array<out UsageInfo>) -> Boolean
): Boolean {
    val container = parentsWithSelf.firstOrNull {
        it is KtNamedFunction || it is KtConstructor<*> || it is KtClassOrObject
    } as? KtNamedDeclaration ?: return false
    val body = container.getDeclarationBody() ?: return false
    return body.textRange.contains(textRange) && container.isCaller(allUsages)
}
fun KtNamedDeclaration.deleteWithCompanion() {
    val containingClass = this.containingClassOrObject
    if (containingClass is KtObjectDeclaration &&
        containingClass.isCompanion() &&
        containingClass.declarations.size == 1 &&
        containingClass.getSuperTypeList() == null
    ) {
        containingClass.delete()
    } else {
        this.delete()
    }
}

fun PsiElement.getAllExtractionContainers(strict: Boolean = true): List<KtElement> {
    val containers = ArrayList<KtElement>()

    var objectOrNonInnerNestedClassFound = false
    val parents = if (strict) parents else parentsWithSelf
    for (element in parents) {
        val isValidContainer = when (element) {
            is KtFile -> true
            is KtClassBody -> !objectOrNonInnerNestedClassFound || element.parent is KtObjectDeclaration
            is KtBlockExpression -> !objectOrNonInnerNestedClassFound
            else -> false
        }
        if (!isValidContainer) continue

        containers.add(element as KtElement)

        if (!objectOrNonInnerNestedClassFound) {
            val bodyParent = (element as? KtClassBody)?.parent
            objectOrNonInnerNestedClassFound =
                (bodyParent is KtObjectDeclaration && !bodyParent.isObjectLiteral())
                        || (bodyParent is KtClass && !bodyParent.isInner())
        }
    }

    return containers
}

fun PsiElement.getExtractionContainers(strict: Boolean = true, includeAll: Boolean = false): List<KtElement> {
    fun getEnclosingDeclaration(element: PsiElement, strict: Boolean): PsiElement? {
        return (if (strict) element.parents else element.parentsWithSelf)
            .filter {
                (it is KtDeclarationWithBody && it !is KtFunctionLiteral && !(it is KtNamedFunction && it.name == null))
                        || it is KtAnonymousInitializer
                        || it is KtClassBody
                        || it is KtFile
            }
            .firstOrNull()
    }

    if (includeAll) return getAllExtractionContainers(strict)

    val enclosingDeclaration = getEnclosingDeclaration(this, strict)?.let {
        if (it is KtDeclarationWithBody || it is KtAnonymousInitializer) getEnclosingDeclaration(it, true) else it
    }

    return when (enclosingDeclaration) {
        is KtFile -> Collections.singletonList(enclosingDeclaration)
        is KtClassBody -> getAllExtractionContainers(strict).filterIsInstance<KtClassBody>()
        else -> {
            val targetContainer = when (enclosingDeclaration) {
                is KtDeclarationWithBody -> enclosingDeclaration.bodyExpression
                is KtAnonymousInitializer -> enclosingDeclaration.body
                else -> null
            }
            if (targetContainer is KtBlockExpression) Collections.singletonList(targetContainer) else Collections.emptyList()
        }
    }
}