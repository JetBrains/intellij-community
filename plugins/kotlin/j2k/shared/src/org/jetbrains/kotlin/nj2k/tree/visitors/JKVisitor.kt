// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.tree.visitors

import org.jetbrains.kotlin.nj2k.tree.JKAnnotation
import org.jetbrains.kotlin.nj2k.tree.JKAnnotationList
import org.jetbrains.kotlin.nj2k.tree.JKAnnotationNameParameter
import org.jetbrains.kotlin.nj2k.tree.JKAnnotationParameter
import org.jetbrains.kotlin.nj2k.tree.JKAnnotationParameterImpl
import org.jetbrains.kotlin.nj2k.tree.JKArgument
import org.jetbrains.kotlin.nj2k.tree.JKArgumentImpl
import org.jetbrains.kotlin.nj2k.tree.JKArgumentList
import org.jetbrains.kotlin.nj2k.tree.JKArrayAccessExpression
import org.jetbrains.kotlin.nj2k.tree.JKAssignmentChainAlsoLink
import org.jetbrains.kotlin.nj2k.tree.JKAssignmentChainLetLink
import org.jetbrains.kotlin.nj2k.tree.JKBinaryExpression
import org.jetbrains.kotlin.nj2k.tree.JKBlock
import org.jetbrains.kotlin.nj2k.tree.JKBlockImpl
import org.jetbrains.kotlin.nj2k.tree.JKBlockStatement
import org.jetbrains.kotlin.nj2k.tree.JKBlockStatementWithoutBrackets
import org.jetbrains.kotlin.nj2k.tree.JKBreakStatement
import org.jetbrains.kotlin.nj2k.tree.JKCallExpression
import org.jetbrains.kotlin.nj2k.tree.JKCallExpressionImpl
import org.jetbrains.kotlin.nj2k.tree.JKClass
import org.jetbrains.kotlin.nj2k.tree.JKClassAccessExpression
import org.jetbrains.kotlin.nj2k.tree.JKClassBody
import org.jetbrains.kotlin.nj2k.tree.JKClassLiteralExpression
import org.jetbrains.kotlin.nj2k.tree.JKConstructor
import org.jetbrains.kotlin.nj2k.tree.JKConstructorImpl
import org.jetbrains.kotlin.nj2k.tree.JKContinueStatement
import org.jetbrains.kotlin.nj2k.tree.JKDeclaration
import org.jetbrains.kotlin.nj2k.tree.JKDeclarationStatement
import org.jetbrains.kotlin.nj2k.tree.JKDelegationConstructorCall
import org.jetbrains.kotlin.nj2k.tree.JKDoWhileStatement
import org.jetbrains.kotlin.nj2k.tree.JKElement
import org.jetbrains.kotlin.nj2k.tree.JKEmptyStatement
import org.jetbrains.kotlin.nj2k.tree.JKEnumConstant
import org.jetbrains.kotlin.nj2k.tree.JKErrorExpression
import org.jetbrains.kotlin.nj2k.tree.JKErrorStatement
import org.jetbrains.kotlin.nj2k.tree.JKExpression
import org.jetbrains.kotlin.nj2k.tree.JKExpressionStatement
import org.jetbrains.kotlin.nj2k.tree.JKField
import org.jetbrains.kotlin.nj2k.tree.JKFieldAccessExpression
import org.jetbrains.kotlin.nj2k.tree.JKFile
import org.jetbrains.kotlin.nj2k.tree.JKForInStatement
import org.jetbrains.kotlin.nj2k.tree.JKForLoopParameter
import org.jetbrains.kotlin.nj2k.tree.JKIfElseExpression
import org.jetbrains.kotlin.nj2k.tree.JKIfElseStatement
import org.jetbrains.kotlin.nj2k.tree.JKImportList
import org.jetbrains.kotlin.nj2k.tree.JKImportStatement
import org.jetbrains.kotlin.nj2k.tree.JKInheritanceInfo
import org.jetbrains.kotlin.nj2k.tree.JKIsExpression
import org.jetbrains.kotlin.nj2k.tree.JKJavaAnnotationMethod
import org.jetbrains.kotlin.nj2k.tree.JKJavaArrowSwitchLabelCase
import org.jetbrains.kotlin.nj2k.tree.JKJavaAssertStatement
import org.jetbrains.kotlin.nj2k.tree.JKJavaAssignmentExpression
import org.jetbrains.kotlin.nj2k.tree.JKJavaClassicLabelSwitchCase
import org.jetbrains.kotlin.nj2k.tree.JKJavaDefaultSwitchCase
import org.jetbrains.kotlin.nj2k.tree.JKJavaForLoopStatement
import org.jetbrains.kotlin.nj2k.tree.JKJavaLabelSwitchCase
import org.jetbrains.kotlin.nj2k.tree.JKJavaNewArray
import org.jetbrains.kotlin.nj2k.tree.JKJavaNewEmptyArray
import org.jetbrains.kotlin.nj2k.tree.JKJavaStaticInitDeclaration
import org.jetbrains.kotlin.nj2k.tree.JKJavaSwitchCase
import org.jetbrains.kotlin.nj2k.tree.JKJavaSwitchExpression
import org.jetbrains.kotlin.nj2k.tree.JKJavaSwitchStatement
import org.jetbrains.kotlin.nj2k.tree.JKJavaSynchronizedStatement
import org.jetbrains.kotlin.nj2k.tree.JKJavaTryCatchSection
import org.jetbrains.kotlin.nj2k.tree.JKJavaTryStatement
import org.jetbrains.kotlin.nj2k.tree.JKJavaYieldStatement
import org.jetbrains.kotlin.nj2k.tree.JKKtAnnotationArrayInitializerExpression
import org.jetbrains.kotlin.nj2k.tree.JKKtAssignmentChainLink
import org.jetbrains.kotlin.nj2k.tree.JKKtAssignmentStatement
import org.jetbrains.kotlin.nj2k.tree.JKKtConvertedFromForLoopSyntheticWhileStatement
import org.jetbrains.kotlin.nj2k.tree.JKKtDestructuringDeclaration
import org.jetbrains.kotlin.nj2k.tree.JKKtDestructuringDeclarationEntry
import org.jetbrains.kotlin.nj2k.tree.JKKtElseWhenLabel
import org.jetbrains.kotlin.nj2k.tree.JKKtInitDeclaration
import org.jetbrains.kotlin.nj2k.tree.JKKtItExpression
import org.jetbrains.kotlin.nj2k.tree.JKKtPrimaryConstructor
import org.jetbrains.kotlin.nj2k.tree.JKKtTryCatchSection
import org.jetbrains.kotlin.nj2k.tree.JKKtTryExpression
import org.jetbrains.kotlin.nj2k.tree.JKKtValueWhenLabel
import org.jetbrains.kotlin.nj2k.tree.JKKtWhenBlock
import org.jetbrains.kotlin.nj2k.tree.JKKtWhenCase
import org.jetbrains.kotlin.nj2k.tree.JKKtWhenExpression
import org.jetbrains.kotlin.nj2k.tree.JKKtWhenLabel
import org.jetbrains.kotlin.nj2k.tree.JKKtWhenStatement
import org.jetbrains.kotlin.nj2k.tree.JKLabel
import org.jetbrains.kotlin.nj2k.tree.JKLabelEmpty
import org.jetbrains.kotlin.nj2k.tree.JKLabelText
import org.jetbrains.kotlin.nj2k.tree.JKLabeledExpression
import org.jetbrains.kotlin.nj2k.tree.JKLambdaExpression
import org.jetbrains.kotlin.nj2k.tree.JKLiteralExpression
import org.jetbrains.kotlin.nj2k.tree.JKLocalVariable
import org.jetbrains.kotlin.nj2k.tree.JKLoopStatement
import org.jetbrains.kotlin.nj2k.tree.JKMethod
import org.jetbrains.kotlin.nj2k.tree.JKMethodAccessExpression
import org.jetbrains.kotlin.nj2k.tree.JKMethodImpl
import org.jetbrains.kotlin.nj2k.tree.JKMethodReferenceExpression
import org.jetbrains.kotlin.nj2k.tree.JKModalityModifierElement
import org.jetbrains.kotlin.nj2k.tree.JKModifierElement
import org.jetbrains.kotlin.nj2k.tree.JKMutabilityModifierElement
import org.jetbrains.kotlin.nj2k.tree.JKNameIdentifier
import org.jetbrains.kotlin.nj2k.tree.JKNamedArgument
import org.jetbrains.kotlin.nj2k.tree.JKNewExpression
import org.jetbrains.kotlin.nj2k.tree.JKOperatorExpression
import org.jetbrains.kotlin.nj2k.tree.JKOtherModifierElement
import org.jetbrains.kotlin.nj2k.tree.JKPackageAccessExpression
import org.jetbrains.kotlin.nj2k.tree.JKPackageDeclaration
import org.jetbrains.kotlin.nj2k.tree.JKParameter
import org.jetbrains.kotlin.nj2k.tree.JKParenthesizedExpression
import org.jetbrains.kotlin.nj2k.tree.JKPostfixExpression
import org.jetbrains.kotlin.nj2k.tree.JKPrefixExpression
import org.jetbrains.kotlin.nj2k.tree.JKQualifiedExpression
import org.jetbrains.kotlin.nj2k.tree.JKReturnStatement
import org.jetbrains.kotlin.nj2k.tree.JKStatement
import org.jetbrains.kotlin.nj2k.tree.JKStubExpression
import org.jetbrains.kotlin.nj2k.tree.JKSuperExpression
import org.jetbrains.kotlin.nj2k.tree.JKThisExpression
import org.jetbrains.kotlin.nj2k.tree.JKThrowExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeRoot
import org.jetbrains.kotlin.nj2k.tree.JKTypeArgumentList
import org.jetbrains.kotlin.nj2k.tree.JKTypeCastExpression
import org.jetbrains.kotlin.nj2k.tree.JKTypeElement
import org.jetbrains.kotlin.nj2k.tree.JKTypeParameter
import org.jetbrains.kotlin.nj2k.tree.JKTypeParameterList
import org.jetbrains.kotlin.nj2k.tree.JKTypeQualifierExpression
import org.jetbrains.kotlin.nj2k.tree.JKUnaryExpression
import org.jetbrains.kotlin.nj2k.tree.JKVariable
import org.jetbrains.kotlin.nj2k.tree.JKVisibilityModifierElement
import org.jetbrains.kotlin.nj2k.tree.JKWhileStatement

abstract class JKVisitor {
    abstract fun visitTreeElement(treeElement: JKElement)
    open fun visitDeclaration(declaration: JKDeclaration) = visitTreeElement(declaration)
    open fun visitClass(klass: JKClass) = visitDeclaration(klass)
    open fun visitVariable(variable: JKVariable) = visitDeclaration(variable)
    open fun visitLocalVariable(localVariable: JKLocalVariable) = visitVariable(localVariable)
    open fun visitForLoopParameter(forLoopParameter: JKForLoopParameter) = visitParameter(forLoopParameter)
    open fun visitParameter(parameter: JKParameter) = visitVariable(parameter)
    open fun visitDestructuringDeclaration(destructuringDeclaration: JKKtDestructuringDeclaration) = visitVariable(destructuringDeclaration)
    open fun visitDestructuringDeclarationEntry(destructuringDeclarationEntry: JKKtDestructuringDeclarationEntry) =
        visitVariable(destructuringDeclarationEntry)

    open fun visitEnumConstant(enumConstant: JKEnumConstant) = visitVariable(enumConstant)
    open fun visitTypeParameter(typeParameter: JKTypeParameter) = visitDeclaration(typeParameter)
    open fun visitMethod(method: JKMethod) = visitDeclaration(method)
    open fun visitMethodImpl(methodImpl: JKMethodImpl) = visitMethod(methodImpl)
    open fun visitConstructor(constructor: JKConstructor) = visitMethod(constructor)
    open fun visitConstructorImpl(constructorImpl: JKConstructorImpl) = visitConstructor(constructorImpl)
    open fun visitKtPrimaryConstructor(ktPrimaryConstructor: JKKtPrimaryConstructor) = visitConstructor(ktPrimaryConstructor)
    open fun visitField(field: JKField) = visitVariable(field)
    open fun visitKtInitDeclaration(ktInitDeclaration: JKKtInitDeclaration) = visitDeclaration(ktInitDeclaration)
    open fun visitJavaStaticInitDeclaration(javaStaticInitDeclaration: JKJavaStaticInitDeclaration) =
        visitDeclaration(javaStaticInitDeclaration)

    open fun visitTreeRoot(treeRoot: JKTreeRoot) = visitTreeElement(treeRoot)
    open fun visitFile(file: JKFile) = visitTreeElement(file)
    open fun visitTypeElement(typeElement: JKTypeElement) = visitTreeElement(typeElement)
    open fun visitBlock(block: JKBlock) = visitTreeElement(block)
    open fun visitInheritanceInfo(inheritanceInfo: JKInheritanceInfo) = visitTreeElement(inheritanceInfo)
    open fun visitPackageDeclaration(packageDeclaration: JKPackageDeclaration) = visitTreeElement(packageDeclaration)
    open fun visitLabel(label: JKLabel) = visitTreeElement(label)
    open fun visitLabelEmpty(labelEmpty: JKLabelEmpty) = visitLabel(labelEmpty)
    open fun visitLabelText(labelText: JKLabelText) = visitLabel(labelText)
    open fun visitImportStatement(importStatement: JKImportStatement) = visitTreeElement(importStatement)
    open fun visitImportList(importList: JKImportList) = visitTreeElement(importList)
    open fun visitAnnotationParameter(annotationParameter: JKAnnotationParameter) = visitTreeElement(annotationParameter)
    open fun visitAnnotationParameterImpl(annotationParameterImpl: JKAnnotationParameterImpl) =
        visitAnnotationParameter(annotationParameterImpl)

    open fun visitAnnotationNameParameter(annotationNameParameter: JKAnnotationNameParameter) =
        visitAnnotationParameter(annotationNameParameter)

    open fun visitArgument(argument: JKArgument) = visitTreeElement(argument)
    open fun visitNamedArgument(namedArgument: JKNamedArgument) = visitArgument(namedArgument)
    open fun visitArgumentImpl(argumentImpl: JKArgumentImpl) = visitArgument(argumentImpl)
    open fun visitArgumentList(argumentList: JKArgumentList) = visitTreeElement(argumentList)
    open fun visitTypeParameterList(typeParameterList: JKTypeParameterList) = visitTreeElement(typeParameterList)
    open fun visitAnnotationList(annotationList: JKAnnotationList) = visitTreeElement(annotationList)
    open fun visitAnnotation(annotation: JKAnnotation) = visitTreeElement(annotation)
    open fun visitTypeArgumentList(typeArgumentList: JKTypeArgumentList) = visitTreeElement(typeArgumentList)
    open fun visitNameIdentifier(nameIdentifier: JKNameIdentifier) = visitTreeElement(nameIdentifier)
    open fun visitBlockImpl(blockImpl: JKBlockImpl) = visitBlock(blockImpl)
    open fun visitKtWhenCase(ktWhenCase: JKKtWhenCase) = visitTreeElement(ktWhenCase)
    open fun visitKtWhenLabel(ktWhenLabel: JKKtWhenLabel) = visitTreeElement(ktWhenLabel)
    open fun visitKtElseWhenLabel(ktElseWhenLabel: JKKtElseWhenLabel) = visitKtWhenLabel(ktElseWhenLabel)
    open fun visitKtValueWhenLabel(ktValueWhenLabel: JKKtValueWhenLabel) = visitKtWhenLabel(ktValueWhenLabel)
    open fun visitClassBody(classBody: JKClassBody) = visitTreeElement(classBody)
    open fun visitJavaTryCatchSection(javaTryCatchSection: JKJavaTryCatchSection) = visitStatement(javaTryCatchSection)
    open fun visitJavaSwitchCase(javaSwitchCase: JKJavaSwitchCase) = visitTreeElement(javaSwitchCase)
    open fun visitJavaDefaultSwitchCase(javaDefaultSwitchCase: JKJavaDefaultSwitchCase) = visitJavaSwitchCase(javaDefaultSwitchCase)
    open fun visitJavaLabelSwitchCase(javaLabelSwitchCase: JKJavaLabelSwitchCase) = visitJavaSwitchCase(javaLabelSwitchCase)
    open fun visitJavaClassicLabelSwitchCase(javaClassicLabelSwitchCase: JKJavaClassicLabelSwitchCase) =
        visitJavaLabelSwitchCase(javaClassicLabelSwitchCase)

    open fun visitJavaArrowLabelSwitchCase(javaArrowSwitchLabelCase: JKJavaArrowSwitchLabelCase) =
        visitJavaLabelSwitchCase(javaArrowSwitchLabelCase)


    open fun visitExpression(expression: JKExpression) = visitTreeElement(expression)
    open fun visitOperatorExpression(operatorExpression: JKOperatorExpression) = visitExpression(operatorExpression)
    open fun visitBinaryExpression(binaryExpression: JKBinaryExpression) = visitOperatorExpression(binaryExpression)
    open fun visitUnaryExpression(unaryExpression: JKUnaryExpression) = visitOperatorExpression(unaryExpression)
    open fun visitPrefixExpression(prefixExpression: JKPrefixExpression) = visitUnaryExpression(prefixExpression)
    open fun visitPostfixExpression(postfixExpression: JKPostfixExpression) = visitUnaryExpression(postfixExpression)
    open fun visitQualifiedExpression(qualifiedExpression: JKQualifiedExpression) = visitExpression(qualifiedExpression)
    open fun visitArrayAccessExpression(arrayAccessExpression: JKArrayAccessExpression) = visitExpression(arrayAccessExpression)
    open fun visitParenthesizedExpression(parenthesizedExpression: JKParenthesizedExpression) = visitExpression(parenthesizedExpression)
    open fun visitTypeCastExpression(typeCastExpression: JKTypeCastExpression) = visitExpression(typeCastExpression)
    open fun visitLiteralExpression(literalExpression: JKLiteralExpression) = visitExpression(literalExpression)
    open fun visitStubExpression(stubExpression: JKStubExpression) = visitExpression(stubExpression)
    open fun visitThisExpression(thisExpression: JKThisExpression) = visitExpression(thisExpression)
    open fun visitSuperExpression(superExpression: JKSuperExpression) = visitExpression(superExpression)
    open fun visitIfElseExpression(ifElseExpression: JKIfElseExpression) = visitExpression(ifElseExpression)
    open fun visitLambdaExpression(lambdaExpression: JKLambdaExpression) = visitExpression(lambdaExpression)
    open fun visitCallExpression(callExpression: JKCallExpression) = visitExpression(callExpression)
    open fun visitDelegationConstructorCall(delegationConstructorCall: JKDelegationConstructorCall) =
        visitCallExpression(delegationConstructorCall)

    open fun visitCallExpressionImpl(callExpressionImpl: JKCallExpressionImpl) = visitCallExpression(callExpressionImpl)
    open fun visitNewExpression(newExpression: JKNewExpression) = visitExpression(newExpression)
    open fun visitFieldAccessExpression(fieldAccessExpression: JKFieldAccessExpression) = visitExpression(fieldAccessExpression)
    open fun visitPackageAccessExpression(packageAccessExpression: JKPackageAccessExpression) = visitExpression(packageAccessExpression)
    open fun visitClassAccessExpression(classAccessExpression: JKClassAccessExpression) = visitExpression(classAccessExpression)
    open fun visitMethodAccessExpression(methodAccessExpression: JKMethodAccessExpression) = visitExpression(methodAccessExpression)
    open fun visitTypeQualifierExpression(typeQualifierExpression: JKTypeQualifierExpression) = visitExpression(typeQualifierExpression)
    open fun visitMethodReferenceExpression(methodReferenceExpression: JKMethodReferenceExpression) =
        visitExpression(methodReferenceExpression)

    open fun visitLabeledExpression(labeledExpression: JKLabeledExpression) = visitExpression(labeledExpression)
    open fun visitClassLiteralExpression(classLiteralExpression: JKClassLiteralExpression) = visitExpression(classLiteralExpression)
    open fun visitKtAssignmentChainLink(ktAssignmentChainLink: JKKtAssignmentChainLink) = visitExpression(ktAssignmentChainLink)
    open fun visitAssignmentChainAlsoLink(assignmentChainAlsoLink: JKAssignmentChainAlsoLink) =
        visitKtAssignmentChainLink(assignmentChainAlsoLink)

    open fun visitAssignmentChainLetLink(assignmentChainLetLink: JKAssignmentChainLetLink) =
        visitKtAssignmentChainLink(assignmentChainLetLink)

    open fun visitIsExpression(isExpression: JKIsExpression) = visitExpression(isExpression)
    open fun visitKtThrowExpression(ktThrowExpression: JKThrowExpression) = visitExpression(ktThrowExpression)
    open fun visitKtItExpression(ktItExpression: JKKtItExpression) = visitExpression(ktItExpression)
    open fun visitKtAnnotationArrayInitializerExpression(ktAnnotationArrayInitializerExpression: JKKtAnnotationArrayInitializerExpression) =
        visitExpression(ktAnnotationArrayInitializerExpression)

    open fun visitJavaSwitchExpression(javaSwitchExpression: JKJavaSwitchExpression) = visitExpression(javaSwitchExpression)


    open fun visitKtWhenBlock(ktWhenBlock: JKKtWhenBlock) = visitTreeElement(ktWhenBlock)
    open fun visitKtWhenExpression(ktWhenExpression: JKKtWhenExpression) = visitKtWhenBlock(ktWhenExpression)

    open fun visitKtTryExpression(ktTryExpression: JKKtTryExpression) = visitExpression(ktTryExpression)
    open fun visitKtTryCatchSection(ktTryCatchSection: JKKtTryCatchSection) = visitTreeElement(ktTryCatchSection)
    open fun visitJavaNewEmptyArray(javaNewEmptyArray: JKJavaNewEmptyArray) = visitExpression(javaNewEmptyArray)
    open fun visitJavaNewArray(javaNewArray: JKJavaNewArray) = visitExpression(javaNewArray)
    open fun visitJavaAssignmentExpression(javaAssignmentExpression: JKJavaAssignmentExpression) = visitExpression(javaAssignmentExpression)
    open fun visitModifierElement(modifierElement: JKModifierElement) = visitTreeElement(modifierElement)
    open fun visitMutabilityModifierElement(mutabilityModifierElement: JKMutabilityModifierElement) =
        visitModifierElement(mutabilityModifierElement)

    open fun visitModalityModifierElement(modalityModifierElement: JKModalityModifierElement) =
        visitModifierElement(modalityModifierElement)

    open fun visitVisibilityModifierElement(visibilityModifierElement: JKVisibilityModifierElement) =
        visitModifierElement(visibilityModifierElement)

    open fun visitOtherModifierElement(otherModifierElement: JKOtherModifierElement) = visitModifierElement(otherModifierElement)
    open fun visitStatement(statement: JKStatement) = visitTreeElement(statement)
    open fun visitEmptyStatement(emptyStatement: JKEmptyStatement) = visitStatement(emptyStatement)
    open fun visitLoopStatement(loopStatement: JKLoopStatement) = visitStatement(loopStatement)
    open fun visitWhileStatement(whileStatement: JKWhileStatement) = visitLoopStatement(whileStatement)
    open fun visitDoWhileStatement(doWhileStatement: JKDoWhileStatement) = visitLoopStatement(doWhileStatement)
    open fun visitForInStatement(forInStatement: JKForInStatement) = visitStatement(forInStatement)
    open fun visitIfElseStatement(ifElseStatement: JKIfElseStatement) = visitStatement(ifElseStatement)
    open fun visitBreakStatement(breakStatement: JKBreakStatement) = visitStatement(breakStatement)
    open fun visitJavaYieldStatement(javaYieldStatement: JKJavaYieldStatement) = visitStatement(javaYieldStatement)
    open fun visitContinueStatement(continueStatement: JKContinueStatement) = visitStatement(continueStatement)
    open fun visitBlockStatement(blockStatement: JKBlockStatement) = visitStatement(blockStatement)
    open fun visitBlockStatementWithoutBrackets(blockStatementWithoutBrackets: JKBlockStatementWithoutBrackets) =
        visitStatement(blockStatementWithoutBrackets)

    open fun visitExpressionStatement(expressionStatement: JKExpressionStatement) = visitStatement(expressionStatement)
    open fun visitDeclarationStatement(declarationStatement: JKDeclarationStatement) = visitStatement(declarationStatement)
    open fun visitKtWhenStatement(ktWhenStatement: JKKtWhenStatement) = visitKtWhenBlock(ktWhenStatement)
    open fun visitKtConvertedFromForLoopSyntheticWhileStatement(ktConvertedFromForLoopSyntheticWhileStatement: JKKtConvertedFromForLoopSyntheticWhileStatement) =
        visitStatement(ktConvertedFromForLoopSyntheticWhileStatement)

    open fun visitKtAssignmentStatement(ktAssignmentStatement: JKKtAssignmentStatement) = visitStatement(ktAssignmentStatement)
    open fun visitReturnStatement(returnStatement: JKReturnStatement) = visitStatement(returnStatement)
    open fun visitJavaSwitchStatement(javaSwitchStatement: JKJavaSwitchStatement) = visitStatement(javaSwitchStatement)
    open fun visitJavaTryStatement(javaTryStatement: JKJavaTryStatement) = visitStatement(javaTryStatement)
    open fun visitJavaSynchronizedStatement(javaSynchronizedStatement: JKJavaSynchronizedStatement) =
        visitStatement(javaSynchronizedStatement)

    open fun visitJavaAssertStatement(javaAssertStatement: JKJavaAssertStatement) = visitStatement(javaAssertStatement)
    open fun visitJavaForLoopStatement(javaForLoopStatement: JKJavaForLoopStatement) = visitLoopStatement(javaForLoopStatement)
    open fun visitJavaAnnotationMethod(javaAnnotationMethod: JKJavaAnnotationMethod) = visitMethod(javaAnnotationMethod)

    open fun visitErrorStatement(errorStatement: JKErrorStatement) = visitStatement(errorStatement)
    open fun visitErrorExpression(errorExpression: JKErrorExpression) = visitExpression(errorExpression)
}
