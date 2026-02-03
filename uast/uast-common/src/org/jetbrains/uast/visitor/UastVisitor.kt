// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPatternExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
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

/**
 * A visitor for UAST elements.
 *
 * When an instance is passed to any [UElement]'s [UElement.accept] function, the appropriate `visit*` function will be called, depending
 * on the actual type of the element.
 *
 * The default implementation for each `visit*` function other than [visitElement] is to delegate to the `visit*` function for the element's
 * supertype. That lets you implement only the most general `visit*` method that applies to your use case. For example, if you want to visit
 * all variables, you can implement [visitVariable] instead of [visitParameter], [visitField], and [visitLocalVariable].
 *
 * To visit the element's children as well, return `false` from the `visit*` function.
 *
 * If the `visit*` function returns `false`, then the visitor will be passed to the `accept` function of each of the direct children of the
 * element, and then the visitor's `afterVisit*` will be called for the element's type. The default implementation for each `afterVisit*`
 * function other than [afterVisitElement] is to delegate to the `afterVisit*` function for the element's supertype.
 */
interface UastVisitor {
  fun visitElement(node: UElement): Boolean

  fun visitFile(node: UFile): Boolean = visitElement(node)
  fun visitImportStatement(node: UImportStatement): Boolean = visitElement(node)
  fun visitDeclaration(node: UDeclaration): Boolean = visitElement(node)
  fun visitClass(node: UClass): Boolean = visitDeclaration(node)
  fun visitInitializer(node: UClassInitializer): Boolean = visitDeclaration(node)
  fun visitMethod(node: UMethod): Boolean = visitDeclaration(node)
  fun visitVariable(node: UVariable): Boolean = visitDeclaration(node)
  fun visitParameter(node: UParameter): Boolean = visitVariable(node)
  fun visitField(node: UField): Boolean = visitVariable(node)
  fun visitLocalVariable(node: ULocalVariable): Boolean = visitVariable(node)
  fun visitEnumConstant(node: UEnumConstant): Boolean = visitField(node)
  fun visitAnnotation(node: UAnnotation): Boolean = visitElement(node)

  // Expressions
  fun visitExpression(node: UExpression): Boolean = visitElement(node)
  fun visitLabeledExpression(node: ULabeledExpression): Boolean = visitExpression(node)
  fun visitDeclarationsExpression(node: UDeclarationsExpression): Boolean = visitExpression(node)
  fun visitBlockExpression(node: UBlockExpression): Boolean = visitExpression(node)
  fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean = visitExpression(node)
  fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean = visitExpression(node)
  fun visitNamedExpression(node: UNamedExpression): Boolean = visitExpression(node)
  fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean = visitExpression(node)
  fun visitCallExpression(node: UCallExpression): Boolean = visitExpression(node)
  fun visitBinaryExpression(node: UBinaryExpression): Boolean = visitExpression(node)
  fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType): Boolean = visitExpression(node)
  fun visitBinaryExpressionWithPattern(node: UBinaryExpressionWithPattern): Boolean = visitExpression(node)
  fun visitPatternExpression(node: UPatternExpression): Boolean = visitExpression(node)
  fun visitPolyadicExpression(node: UPolyadicExpression): Boolean = visitExpression(node)
  fun visitParenthesizedExpression(node: UParenthesizedExpression): Boolean = visitExpression(node)
  fun visitUnaryExpression(node: UUnaryExpression): Boolean = visitExpression(node)
  fun visitPrefixExpression(node: UPrefixExpression): Boolean = visitExpression(node)
  fun visitPostfixExpression(node: UPostfixExpression): Boolean = visitExpression(node)
  fun visitExpressionList(node: UExpressionList): Boolean = visitExpression(node)
  fun visitIfExpression(node: UIfExpression): Boolean = visitExpression(node)
  fun visitSwitchExpression(node: USwitchExpression): Boolean = visitExpression(node)
  fun visitSwitchClauseExpression(node: USwitchClauseExpression): Boolean = visitExpression(node)
  fun visitWhileExpression(node: UWhileExpression): Boolean = visitExpression(node)
  fun visitDoWhileExpression(node: UDoWhileExpression): Boolean = visitExpression(node)
  fun visitForExpression(node: UForExpression): Boolean = visitExpression(node)
  fun visitForEachExpression(node: UForEachExpression): Boolean = visitExpression(node)
  fun visitTryExpression(node: UTryExpression): Boolean = visitExpression(node)
  fun visitCatchClause(node: UCatchClause): Boolean = visitElement(node)
  fun visitLiteralExpression(node: ULiteralExpression): Boolean = visitExpression(node)
  fun visitThisExpression(node: UThisExpression): Boolean = visitExpression(node)
  fun visitSuperExpression(node: USuperExpression): Boolean = visitExpression(node)
  fun visitReturnExpression(node: UReturnExpression): Boolean = visitExpression(node)
  fun visitBreakExpression(node: UBreakExpression): Boolean = visitExpression(node)
  fun visitYieldExpression(node: UYieldExpression): Boolean = visitExpression(node)
  fun visitContinueExpression(node: UContinueExpression): Boolean = visitExpression(node)
  fun visitThrowExpression(node: UThrowExpression): Boolean = visitExpression(node)
  fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean = visitExpression(node)
  fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean = visitExpression(node)
  fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean = visitExpression(node)
  fun visitLambdaExpression(node: ULambdaExpression): Boolean = visitExpression(node)
  fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean = visitExpression(node)

  fun visitComment(node: UComment): Boolean = visitElement(node)

  // After

  fun afterVisitElement(node: UElement) { }

  fun afterVisitFile(node: UFile): Unit = afterVisitElement(node)
  fun afterVisitImportStatement(node: UImportStatement): Unit = afterVisitElement(node)
  fun afterVisitDeclaration(node: UDeclaration): Unit = afterVisitElement(node)
  fun afterVisitClass(node: UClass): Unit = afterVisitDeclaration(node)
  fun afterVisitInitializer(node: UClassInitializer): Unit = afterVisitDeclaration(node)
  fun afterVisitMethod(node: UMethod): Unit = afterVisitDeclaration(node)
  fun afterVisitVariable(node: UVariable): Unit = afterVisitElement(node)
  fun afterVisitParameter(node: UParameter): Unit = afterVisitVariable(node)
  fun afterVisitField(node: UField): Unit = afterVisitVariable(node)
  fun afterVisitLocalVariable(node: ULocalVariable): Unit = afterVisitVariable(node)
  fun afterVisitEnumConstant(node: UEnumConstant): Unit = afterVisitField(node)
  fun afterVisitAnnotation(node: UAnnotation): Unit = afterVisitElement(node)
  // Expressions
  fun afterVisitExpression(node: UExpression): Unit = afterVisitElement(node)
  fun afterVisitLabeledExpression(node: ULabeledExpression): Unit = afterVisitExpression(node)
  fun afterVisitDeclarationsExpression(node: UDeclarationsExpression): Unit = afterVisitExpression(node)
  fun afterVisitBlockExpression(node: UBlockExpression): Unit = afterVisitExpression(node)
  fun afterVisitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Unit = afterVisitExpression(node)
  fun afterVisitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Unit = afterVisitExpression(node)
  fun afterVisitNamedExpression(node: UNamedExpression): Unit = afterVisitExpression(node)
  fun afterVisitTypeReferenceExpression(node: UTypeReferenceExpression): Unit = afterVisitExpression(node)
  fun afterVisitCallExpression(node: UCallExpression): Unit = afterVisitExpression(node)
  fun afterVisitBinaryExpression(node: UBinaryExpression): Unit = afterVisitExpression(node)
  fun afterVisitBinaryExpressionWithType(node: UBinaryExpressionWithType): Unit = afterVisitExpression(node)
  fun afterVisitBinaryExpressionWithPattern(node: UBinaryExpressionWithPattern): Unit = afterVisitExpression(node)
  fun afterVisitPatternExpression(node: UPatternExpression): Unit = afterVisitExpression(node)
  fun afterVisitParenthesizedExpression(node: UParenthesizedExpression): Unit = afterVisitExpression(node)
  fun afterVisitUnaryExpression(node: UUnaryExpression): Unit = afterVisitExpression(node)
  fun afterVisitPrefixExpression(node: UPrefixExpression): Unit = afterVisitExpression(node)
  fun afterVisitPostfixExpression(node: UPostfixExpression): Unit = afterVisitExpression(node)
  fun afterVisitExpressionList(node: UExpressionList): Unit = afterVisitExpression(node)
  fun afterVisitIfExpression(node: UIfExpression): Unit = afterVisitExpression(node)
  fun afterVisitSwitchExpression(node: USwitchExpression): Unit = afterVisitExpression(node)
  fun afterVisitSwitchClauseExpression(node: USwitchClauseExpression): Unit = afterVisitExpression(node)
  fun afterVisitWhileExpression(node: UWhileExpression): Unit = afterVisitExpression(node)
  fun afterVisitDoWhileExpression(node: UDoWhileExpression): Unit = afterVisitExpression(node)
  fun afterVisitForExpression(node: UForExpression): Unit = afterVisitExpression(node)
  fun afterVisitForEachExpression(node: UForEachExpression): Unit = afterVisitExpression(node)
  fun afterVisitTryExpression(node: UTryExpression): Unit = afterVisitExpression(node)
  fun afterVisitCatchClause(node: UCatchClause): Unit = afterVisitElement(node)
  fun afterVisitLiteralExpression(node: ULiteralExpression): Unit = afterVisitExpression(node)
  fun afterVisitThisExpression(node: UThisExpression): Unit = afterVisitExpression(node)
  fun afterVisitSuperExpression(node: USuperExpression): Unit = afterVisitExpression(node)
  fun afterVisitReturnExpression(node: UReturnExpression): Unit = afterVisitExpression(node)
  fun afterVisitBreakExpression(node: UBreakExpression): Unit = afterVisitExpression(node)
  fun afterVisitYieldExpression(node: UYieldExpression): Unit = afterVisitExpression(node)
  fun afterVisitContinueExpression(node: UContinueExpression): Unit = afterVisitExpression(node)
  fun afterVisitThrowExpression(node: UThrowExpression): Unit = afterVisitExpression(node)
  fun afterVisitArrayAccessExpression(node: UArrayAccessExpression): Unit = afterVisitExpression(node)
  fun afterVisitCallableReferenceExpression(node: UCallableReferenceExpression): Unit = afterVisitExpression(node)
  fun afterVisitClassLiteralExpression(node: UClassLiteralExpression): Unit = afterVisitExpression(node)
  fun afterVisitLambdaExpression(node: ULambdaExpression): Unit = afterVisitExpression(node)
  fun afterVisitObjectLiteralExpression(node: UObjectLiteralExpression): Unit = afterVisitExpression(node)
  fun afterVisitPolyadicExpression(node: UPolyadicExpression): Unit = afterVisitExpression(node)

  fun afterVisitComment(node: UComment): Unit = afterVisitElement(node)
}

/**
 * A [UastVisitor] that visits each element's children by default.
 */
abstract class AbstractUastVisitor : UastVisitor {
  override fun visitElement(node: UElement): Boolean = false
}

/**
 * A [UastVisitor] that does not visit each element's children by default.
 */
abstract class AbstractUastNonRecursiveVisitor : UastVisitor {
  override fun visitElement(node: UElement): Boolean = true
}

/**
 * A [UastVisitor] that visits each element's children but does nothing at each element.
 */
@Suppress("unused")
object EmptyUastVisitor : AbstractUastVisitor()