// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.siblings

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

fun KtCallExpression.moveFunctionLiteralOutsideParentheses() {
    assert(lambdaArguments.isEmpty())
    val argumentList = valueArgumentList!!
    val argument = argumentList.arguments.last()
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
    } else {
        argumentList.removeArgument(argument)
    }
}