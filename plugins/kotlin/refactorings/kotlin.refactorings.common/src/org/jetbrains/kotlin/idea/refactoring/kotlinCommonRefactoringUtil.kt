// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.refactoring.util.ConflictsUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import java.util.*
import kotlin.math.min

/**
 * Get the element that specifies the name of [this] element.
 */
fun PsiElement.nameDeterminant() = when {
    this is KtConstructor<*> -> containingClass() ?: error("Constructor had no containing class")
    this is PsiMethod && isConstructor -> containingClass ?: error("Constructor had no containing class")
    else -> this
} as PsiNamedElement

fun PsiElement.getContainer(): PsiElement {
    return when (this) {
        is KtElement -> PsiTreeUtil.getParentOfType(
            this,
            KtPropertyAccessor::class.java,
            KtParameter::class.java,
            KtProperty::class.java,
            KtNamedFunction::class.java,
            KtConstructor::class.java,
            KtClassOrObject::class.java
        ) ?: containingFile
        else -> ConflictsUtil.getContainer(this)
    }
}

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

fun KtNamedDeclaration.isCompanionMemberOf(klass: KtClassOrObject): Boolean {
    val containingObject = containingClassOrObject as? KtObjectDeclaration ?: return false
    return containingObject.isCompanion() && containingObject.containingClassOrObject == klass
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

context(KaSession)
@ApiStatus.Internal
fun KtCallExpression.canMoveLambdaOutsideParentheses(
    skipComplexCalls: Boolean = true
): Boolean {
    if (skipComplexCalls && isComplexCallWithLambdaArgument()) {
        return false
    }

    if (getStrictParentOfType<KtDelegatedSuperTypeEntry>() != null) {
        return false
    }
    val lastLambdaExpression = getLastLambdaExpression() ?: return false

    if (lastLambdaExpression.parentLabeledExpression()?.parentLabeledExpression() != null) {
        return false
    }

    val callee = calleeExpression
    if (callee !is KtNameReferenceExpression) return true

    val resolveCall = callee.resolveToCall() ?: return false
    val call = resolveCall.successfulFunctionCallOrNull()

    fun KaType.isFunctionalType(): Boolean = this is KaTypeParameterType || isSuspendFunctionType || isFunctionType || isFunctionalInterface

    if (call == null) {
        val paramType = resolveCall.successfulVariableAccessCall()?.partiallyAppliedSymbol?.symbol?.returnType
        if (paramType != null && paramType.isFunctionalType()) {
            return true
        }
        val calls =
            (resolveCall as? KaErrorCallInfo)?.candidateCalls?.filterIsInstance<KaSimpleFunctionCall>() ?:
            emptyList()

        return calls.isEmpty() || calls.all { functionalCall ->
            val lastParameter = functionalCall.partiallyAppliedSymbol.signature.valueParameters.lastOrNull()
            val lastParameterType = lastParameter?.returnType
            lastParameterType != null && lastParameterType.isFunctionalType()
        }
    }

    val lastParameter = call.argumentMapping[lastLambdaExpression]
        ?: lastLambdaExpression.parentLabeledExpression()?.let(call.argumentMapping::get)
        ?: return false

    if (lastParameter.symbol.isVararg) {
        // Passing value as a vararg is allowed only inside a parenthesized argument list
        return false
    }

    return if (lastParameter.symbol != call.partiallyAppliedSymbol.signature.valueParameters.lastOrNull()?.symbol) {
        false
    } else {
        lastParameter.returnType.isFunctionalType()
    }
}

@ApiStatus.Internal
fun KtExpression.parentLabeledExpression(): KtLabeledExpression? {
    return getStrictParentOfType<KtLabeledExpression>()?.takeIf { it.baseExpression == this }
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
            is KtFile -> !element.isScript()
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

fun PsiElement.getExtractionContainers(strict: Boolean = true, includeAll: Boolean = false, acceptScript: Boolean = false): List<KtElement> {
    fun getEnclosingDeclaration(element: PsiElement, strict: Boolean): PsiElement? {
        return (if (strict) element.parents else element.parentsWithSelf)
            .filter {
                (it is KtDeclarationWithBody && it !is KtFunctionLiteral && !(it is KtNamedFunction && it.name == null))
                        || it is KtAnonymousInitializer
                        || it is KtClassBody
                        || it is KtFile
                        || acceptScript && it is KtScript
            }
            .firstOrNull()
    }

    if (includeAll) return getAllExtractionContainers(strict)

    val enclosingDeclaration = getEnclosingDeclaration(this, strict)?.let {
        if (it is KtDeclarationWithBody || it is KtAnonymousInitializer) getEnclosingDeclaration(it, true) else it
    }

    return when (enclosingDeclaration) {
        is KtFile -> Collections.singletonList(enclosingDeclaration)
        is KtScript -> Collections.singletonList(enclosingDeclaration)
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

fun KtBlockExpression.addElement(element: KtElement, addNewLine: Boolean = false): KtElement {
    val rBrace = rBrace
    val newLine = KtPsiFactory(project).createNewLine()
    val anchor = if (rBrace == null) {
        val lastChild = lastChild
        lastChild as? PsiWhiteSpace ?: addAfter(newLine, lastChild)!!
    } else {
        rBrace.prevSibling!!
    }
    val addedElement = addAfter(element, anchor)!! as KtElement
    if (addNewLine) {
        addAfter(newLine, addedElement)
    }
    return addedElement
}

fun Project.checkConflictsInteractively(
    conflicts: MultiMap<PsiElement, String>,
    onShowConflicts: () -> Unit = {},
    onAccept: () -> Unit
) {
    if (!conflicts.isEmpty) {
        if (isUnitTestMode()) throw ConflictsInTestsException(conflicts.values())

        val dialog = ConflictsDialog(this, conflicts) { onAccept() }
        dialog.show()
        if (!dialog.isOK) {
            if (dialog.isShowConflicts) {
                onShowConflicts()
            }
            return
        }
    }

    onAccept()
}

fun FqNameUnsafe.hasIdentifiersOnly(): Boolean = pathSegments().all { it.asString().quoteIfNeeded().isIdentifier() }
fun FqName.hasIdentifiersOnly(): Boolean = pathSegments().all { it.asString().quoteIfNeeded().isIdentifier() }

fun KtCallExpression.singleLambdaArgumentExpression(): KtLambdaExpression? {
    return lambdaArguments.singleOrNull()?.getArgumentExpression()?.unpackFunctionLiteral() ?: getLastLambdaExpression()
}

fun BuilderByPattern<KtExpression>.appendCallOrQualifiedExpression(
    call: KtCallExpression,
    newFunctionName: String
) {
    val callOrQualified = call.getQualifiedExpressionForSelector() ?: call
    if (callOrQualified is KtQualifiedExpression) {
        appendExpression(callOrQualified.receiverExpression)
        if (callOrQualified is KtSafeQualifiedExpression) appendFixedText("?")
        appendFixedText(".")
    }
    appendNonFormattedText(newFunctionName)
    call.valueArgumentList?.let { appendNonFormattedText(it.text) }
    call.lambdaArguments.firstOrNull()?.let {
        if (it.getArgumentExpression() is KtLabeledExpression) appendFixedText(" ")
        appendNonFormattedText(it.text)
    }
}

@ApiStatus.Internal
fun PsiElement.removeOverrideModifier() {
    when (this) {
        is KtNamedFunction, is KtProperty -> {
            (this as KtModifierListOwner).modifierList?.getModifier(KtTokens.OVERRIDE_KEYWORD)?.delete()
        }

        is PsiMethod -> {
            modifierList.annotations.firstOrNull { annotation ->
                annotation.qualifiedName == "java.lang.Override"
            }?.delete()
        }
    }
}