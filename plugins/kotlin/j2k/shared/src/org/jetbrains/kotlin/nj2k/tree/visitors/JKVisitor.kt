// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.tree.visitors

import org.jetbrains.kotlin.nj2k.tree.*

abstract class JKVisitor {
    abstract fun visitTreeElement(treeElement: JKElement)

    abstract fun visitClass(klass: JKClass)

    abstract fun visitLocalVariable(localVariable: JKLocalVariable)

    abstract fun visitForLoopVariable(forLoopVariable: JKForLoopVariable)

    abstract fun visitParameter(parameter: JKParameter)

    abstract fun visitEnumConstant(enumConstant: JKEnumConstant)

    abstract fun visitTypeParameter(typeParameter: JKTypeParameter)

    abstract fun visitMethod(method: JKMethod)

    abstract fun visitMethodImpl(methodImpl: JKMethodImpl)

    abstract fun visitConstructor(constructor: JKConstructor)

    abstract fun visitConstructorImpl(constructorImpl: JKConstructorImpl)

    abstract fun visitKtPrimaryConstructor(ktPrimaryConstructor: JKKtPrimaryConstructor)

    abstract fun visitField(field: JKField)

    abstract fun visitKtInitDeclaration(ktInitDeclaration: JKKtInitDeclaration)

    abstract fun visitTreeRoot(treeRoot: JKTreeRoot)

    abstract fun visitFile(file: JKFile)

    abstract fun visitTypeElement(typeElement: JKTypeElement)

    abstract fun visitBlock(block: JKBlock)

    abstract fun visitInheritanceInfo(inheritanceInfo: JKInheritanceInfo)

    abstract fun visitPackageDeclaration(packageDeclaration: JKPackageDeclaration)

    abstract fun visitLabelEmpty(labelEmpty: JKLabelEmpty)

    abstract fun visitLabelText(labelText: JKLabelText)

    abstract fun visitImportStatement(importStatement: JKImportStatement)

    abstract fun visitImportList(importList: JKImportList)

    abstract fun visitAnnotationParameter(annotationParameter: JKAnnotationParameter)

    abstract fun visitAnnotationParameterImpl(annotationParameterImpl: JKAnnotationParameterImpl)

    abstract fun visitAnnotationNameParameter(annotationNameParameter: JKAnnotationNameParameter)

    abstract fun visitArgument(argument: JKArgument)

    abstract fun visitNamedArgument(namedArgument: JKNamedArgument)

    abstract fun visitArgumentImpl(argumentImpl: JKArgumentImpl)

    abstract fun visitArgumentList(argumentList: JKArgumentList)

    abstract fun visitTypeParameterList(typeParameterList: JKTypeParameterList)

    abstract fun visitAnnotationList(annotationList: JKAnnotationList)

    abstract fun visitAnnotation(annotation: JKAnnotation)

    abstract fun visitTypeArgumentList(typeArgumentList: JKTypeArgumentList)

    abstract fun visitNameIdentifier(nameIdentifier: JKNameIdentifier)

    abstract fun visitBlockImpl(blockImpl: JKBlockImpl)

    abstract fun visitKtWhenCase(ktWhenCase: JKKtWhenCase)

    abstract fun visitKtElseWhenLabel(ktElseWhenLabel: JKKtElseWhenLabel)

    abstract fun visitKtValueWhenLabel(ktValueWhenLabel: JKKtValueWhenLabel)

    abstract fun visitClassBody(classBody: JKClassBody)

    abstract fun visitBinaryExpression(binaryExpression: JKBinaryExpression)

    abstract fun visitPrefixExpression(prefixExpression: JKPrefixExpression)

    abstract fun visitPostfixExpression(postfixExpression: JKPostfixExpression)

    abstract fun visitQualifiedExpression(qualifiedExpression: JKQualifiedExpression)

    abstract fun visitParenthesizedExpression(parenthesizedExpression: JKParenthesizedExpression)

    abstract fun visitTypeCastExpression(typeCastExpression: JKTypeCastExpression)

    abstract fun visitLiteralExpression(literalExpression: JKLiteralExpression)

    abstract fun visitStubExpression(stubExpression: JKStubExpression)

    abstract fun visitThisExpression(thisExpression: JKThisExpression)

    abstract fun visitSuperExpression(superExpression: JKSuperExpression)

    abstract fun visitIfElseExpression(ifElseExpression: JKIfElseExpression)

    abstract fun visitLambdaExpression(lambdaExpression: JKLambdaExpression)

    abstract fun visitDelegationConstructorCall(delegationConstructorCall: JKDelegationConstructorCall)

    abstract fun visitCallExpression(callExpression: JKCallExpression)

    abstract fun visitCallExpressionImpl(callExpressionImpl: JKCallExpressionImpl)

    abstract fun visitNewExpression(newExpression: JKNewExpression)

    abstract fun visitFieldAccessExpression(fieldAccessExpression: JKFieldAccessExpression)

    abstract fun visitPackageAccessExpression(packageAccessExpression: JKPackageAccessExpression)

    abstract fun visitClassAccessExpression(classAccessExpression: JKClassAccessExpression)

    abstract fun visitMethodAccessExpression(methodAccessExpression: JKMethodAccessExpression)

    abstract fun visitTypeQualifierExpression(typeQualifierExpression: JKTypeQualifierExpression)

    abstract fun visitMethodReferenceExpression(methodReferenceExpression: JKMethodReferenceExpression)

    abstract fun visitLabeledExpression(labeledExpression: JKLabeledExpression)

    abstract fun visitClassLiteralExpression(classLiteralExpression: JKClassLiteralExpression)

    abstract fun visitAssignmentChainAlsoLink(assignmentChainAlsoLink: JKAssignmentChainAlsoLink)

    abstract fun visitAssignmentChainLetLink(assignmentChainLetLink: JKAssignmentChainLetLink)

    abstract fun visitIsExpression(isExpression: JKIsExpression)

    abstract fun visitKtThrowExpression(ktThrowExpression: JKThrowExpression)

    abstract fun visitKtItExpression(ktItExpression: JKKtItExpression)

    abstract fun visitKtAnnotationArrayInitializerExpression(ktAnnotationArrayInitializerExpression: JKKtAnnotationArrayInitializerExpression)

    abstract fun visitKtWhenBlock(ktWhenBlock: JKKtWhenBlock)

    abstract fun visitKtWhenExpression(ktWhenExpression: JKKtWhenExpression)

    abstract fun visitKtTryExpression(ktTryExpression: JKKtTryExpression)

    abstract fun visitKtTryCatchSection(ktTryCatchSection: JKKtTryCatchSection)

    abstract fun visitModifierElement(modifierElement: JKModifierElement)

    abstract fun visitMutabilityModifierElement(mutabilityModifierElement: JKMutabilityModifierElement)

    abstract fun visitModalityModifierElement(modalityModifierElement: JKModalityModifierElement)

    abstract fun visitVisibilityModifierElement(visibilityModifierElement: JKVisibilityModifierElement)

    abstract fun visitOtherModifierElement(otherModifierElement: JKOtherModifierElement)

    abstract fun visitEmptyStatement(emptyStatement: JKEmptyStatement)

    abstract fun visitWhileStatement(whileStatement: JKWhileStatement)

    abstract fun visitDoWhileStatement(doWhileStatement: JKDoWhileStatement)

    abstract fun visitForInStatement(forInStatement: JKForInStatement)

    abstract fun visitIfElseStatement(ifElseStatement: JKIfElseStatement)

    abstract fun visitBreakStatement(breakStatement: JKBreakStatement)

    abstract fun visitContinueStatement(continueStatement: JKContinueStatement)

    abstract fun visitBlockStatement(blockStatement: JKBlockStatement)

    abstract fun visitBlockStatementWithoutBrackets(blockStatementWithoutBrackets: JKBlockStatementWithoutBrackets)

    abstract fun visitExpressionStatement(expressionStatement: JKExpressionStatement)

    abstract fun visitDeclarationStatement(declarationStatement: JKDeclarationStatement)

    abstract fun visitKtWhenStatement(ktWhenStatement: JKKtWhenStatement)

    abstract fun visitKtConvertedFromForLoopSyntheticWhileStatement(ktConvertedFromForLoopSyntheticWhileStatement: JKKtConvertedFromForLoopSyntheticWhileStatement)

    abstract fun visitKtAssignmentStatement(ktAssignmentStatement: JKKtAssignmentStatement)

    abstract fun visitReturnStatement(returnStatement: JKReturnStatement)

    abstract fun visitErrorStatement(errorStatement: JKErrorStatement)

    abstract fun visitErrorExpression(errorExpression: JKErrorExpression)
}
