// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.visitor

import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithPattern
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UComment
import org.jetbrains.uast.UContinueExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.ULoopExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPatternExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.UYieldExpression

interface UastTypedVisitor<in D, out R> {
  fun visitElement(node: UElement, data: D): R
  // Just elements
  fun visitFile(node: UFile, data: D): R = visitElement(node, data)

  fun visitImportStatement(node: UImportStatement, data: D): R = visitElement(node, data)
  fun visitAnnotation(node: UAnnotation, data: D): R = visitElement(node, data)
  fun visitCatchClause(node: UCatchClause, data: D): R = visitElement(node, data)
  // Declarations
  fun visitDeclaration(node: UDeclaration, data: D): R = visitElement(node, data)

  fun visitClass(node: UClass, data: D): R = visitDeclaration(node, data)
  fun visitMethod(node: UMethod, data: D): R = visitDeclaration(node, data)
  fun visitClassInitializer(node: UClassInitializer, data: D): R = visitDeclaration(node, data)
  // Variables
  fun visitVariable(node: UVariable, data: D): R = visitDeclaration(node, data)

  fun visitParameter(node: UParameter, data: D): R = visitVariable(node, data)
  fun visitField(node: UField, data: D): R = visitVariable(node, data)
  fun visitLocalVariable(node: ULocalVariable, data: D): R = visitVariable(node, data)
  fun visitEnumConstantExpression(node: UEnumConstant, data: D): R = visitVariable(node, data)
  // Expressions
  fun visitExpression(node: UExpression, data: D): R = visitElement(node, data)

  fun visitLabeledExpression(node: ULabeledExpression, data: D): R = visitExpression(node, data)
  fun visitDeclarationsExpression(node: UDeclarationsExpression, data: D): R = visitExpression(node, data)
  fun visitBlockExpression(node: UBlockExpression, data: D): R = visitExpression(node, data)
  fun visitTypeReferenceExpression(node: UTypeReferenceExpression, data: D): R = visitExpression(node, data)
  fun visitExpressionList(node: UExpressionList, data: D): R = visitExpression(node, data)
  fun visitLiteralExpression(node: ULiteralExpression, data: D): R = visitExpression(node, data)
  fun visitThisExpression(node: UThisExpression, data: D): R = visitExpression(node, data)
  fun visitSuperExpression(node: USuperExpression, data: D): R = visitExpression(node, data)
  fun visitArrayAccessExpression(node: UArrayAccessExpression, data: D): R = visitExpression(node, data)
  fun visitClassLiteralExpression(node: UClassLiteralExpression, data: D): R = visitExpression(node, data)
  fun visitLambdaExpression(node: ULambdaExpression, data: D): R = visitExpression(node, data)
  fun visitPolyadicExpression(node: UPolyadicExpression, data: D): R = visitExpression(node, data)
  // Calls
  fun visitCallExpression(node: UCallExpression, data: D): R = visitExpression(node, data)

  fun visitObjectLiteralExpression(node: UObjectLiteralExpression, data: D): R = visitCallExpression(node, data)
  // Operations
  fun visitBinaryExpression(node: UBinaryExpression, data: D): R = visitPolyadicExpression(node, data)

  fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType, data: D): R = visitExpression(node, data)
  fun visitBinaryExpressionWithPattern(node: UBinaryExpressionWithPattern, data: D): R = visitExpression(node, data)
  fun visitPatternExpression(node: UPatternExpression, data: D): R = visitExpression(node, data)
  fun visitParenthesizedExpression(node: UParenthesizedExpression, data: D): R = visitExpression(node, data)
  // Unary operations
  fun visitUnaryExpression(node: UUnaryExpression, data: D): R = visitExpression(node, data)

  fun visitPrefixExpression(node: UPrefixExpression, data: D): R = visitUnaryExpression(node, data)
  fun visitPostfixExpression(node: UPostfixExpression, data: D): R = visitUnaryExpression(node, data)
  // References
  fun visitReferenceExpression(node: UReferenceExpression, data: D): R = visitExpression(node, data)

  fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression, data: D): R = visitReferenceExpression(node, data)
  fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression, data: D): R = visitReferenceExpression(node, data)
  fun visitCallableReferenceExpression(node: UCallableReferenceExpression, data: D): R = visitReferenceExpression(node, data)
  // Control structures
  fun visitIfExpression(node: UIfExpression, data: D): R = visitExpression(node, data)

  fun visitSwitchExpression(node: USwitchExpression, data: D): R = visitExpression(node, data)
  fun visitSwitchClauseExpression(node: USwitchClauseExpression, data: D): R = visitExpression(node, data)
  fun visitTryExpression(node: UTryExpression, data: D): R = visitExpression(node, data)
  // Jumps
  fun visitReturnExpression(node: UReturnExpression, data: D): R = visitExpression(node, data)

  fun visitBreakExpression(node: UBreakExpression, data: D): R = visitExpression(node, data)
  fun visitYieldExpression(node: UYieldExpression, data: D): R = visitExpression(node, data)
  fun visitContinueExpression(node: UContinueExpression, data: D): R = visitExpression(node, data)
  fun visitThrowExpression(node: UThrowExpression, data: D): R = visitExpression(node, data)
  // Loops
  fun visitLoopExpression(node: ULoopExpression, data: D): R = visitExpression(node, data)

  fun visitWhileExpression(node: UWhileExpression, data: D): R = visitLoopExpression(node, data)
  fun visitDoWhileExpression(node: UDoWhileExpression, data: D): R = visitLoopExpression(node, data)
  fun visitForExpression(node: UForExpression, data: D): R = visitLoopExpression(node, data)
  fun visitForEachExpression(node: UForEachExpression, data: D): R = visitLoopExpression(node, data)

  fun visitComment(node: UComment, data: D): R = visitElement(node, data)
}