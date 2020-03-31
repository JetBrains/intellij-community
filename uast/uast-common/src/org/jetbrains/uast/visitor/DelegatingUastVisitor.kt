// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.visitor

import org.jetbrains.uast.*

class DelegatingUastVisitor(private val visitors: List<UastVisitor>) : UastVisitor {
  override fun visitElement(node: UElement): Boolean {
    return visitors.all { it.visitElement(node) }
  }

  override fun visitVariable(node: UVariable): Boolean {
    return visitors.all { it.visitVariable(node) }
  }

  override fun visitMethod(node: UMethod): Boolean {
    return visitors.all { it.visitMethod(node) }
  }

  override fun visitLabeledExpression(node: ULabeledExpression): Boolean {
    return visitors.all { it.visitLabeledExpression(node) }
  }

  override fun visitDeclarationsExpression(node: UDeclarationsExpression): Boolean {
    return visitors.all { it.visitDeclarationsExpression(node) }
  }

  override fun visitBlockExpression(node: UBlockExpression): Boolean {
    return visitors.all { it.visitBlockExpression(node) }
  }

  override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
    return visitors.all { it.visitQualifiedReferenceExpression(node) }
  }

  override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
    return visitors.all { it.visitSimpleNameReferenceExpression(node) }
  }

  override fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean {
    return visitors.all { it.visitTypeReferenceExpression(node) }
  }

  override fun visitCallExpression(node: UCallExpression): Boolean {
    return visitors.all { it.visitCallExpression(node) }
  }

  override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
    return visitors.all { it.visitBinaryExpression(node) }
  }

  override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType): Boolean {
    return visitors.all { it.visitBinaryExpressionWithType(node) }
  }

  override fun visitParenthesizedExpression(node: UParenthesizedExpression): Boolean {
    return visitors.all { it.visitParenthesizedExpression(node) }
  }

  override fun visitUnaryExpression(node: UUnaryExpression): Boolean {
    return visitors.all { it.visitUnaryExpression(node) }
  }

  override fun visitPrefixExpression(node: UPrefixExpression): Boolean {
    return visitors.all { it.visitPrefixExpression(node) }
  }

  override fun visitPostfixExpression(node: UPostfixExpression): Boolean {
    return visitors.all { it.visitPostfixExpression(node) }
  }

  override fun visitExpressionList(node: UExpressionList): Boolean {
    return visitors.all { it.visitExpressionList(node) }
  }

  override fun visitIfExpression(node: UIfExpression): Boolean {
    return visitors.all { it.visitIfExpression(node) }
  }

  override fun visitSwitchExpression(node: USwitchExpression): Boolean {
    return visitors.all { it.visitSwitchExpression(node) }
  }

  override fun visitSwitchClauseExpression(node: USwitchClauseExpression): Boolean {
    return visitors.all { it.visitSwitchClauseExpression(node) }
  }

  override fun visitWhileExpression(node: UWhileExpression): Boolean {
    return visitors.all { it.visitWhileExpression(node) }
  }

  override fun visitDoWhileExpression(node: UDoWhileExpression): Boolean {
    return visitors.all { it.visitDoWhileExpression(node) }
  }

  override fun visitForExpression(node: UForExpression): Boolean {
    return visitors.all { it.visitForExpression(node) }
  }

  override fun visitForEachExpression(node: UForEachExpression): Boolean {
    return visitors.all { it.visitForEachExpression(node) }
  }

  override fun visitTryExpression(node: UTryExpression): Boolean {
    return visitors.all { it.visitTryExpression(node) }
  }

  override fun visitCatchClause(node: UCatchClause): Boolean {
    return visitors.all { it.visitCatchClause(node) }
  }

  override fun visitLiteralExpression(node: ULiteralExpression): Boolean {
    return visitors.all { it.visitLiteralExpression(node) }
  }

  override fun visitThisExpression(node: UThisExpression): Boolean {
    return visitors.all { it.visitThisExpression(node) }
  }

  override fun visitSuperExpression(node: USuperExpression): Boolean {
    return visitors.all { it.visitSuperExpression(node) }
  }

  override fun visitReturnExpression(node: UReturnExpression): Boolean {
    return visitors.all { it.visitReturnExpression(node) }
  }

  override fun visitBreakExpression(node: UBreakExpression): Boolean {
    return visitors.all { it.visitBreakExpression(node) }
  }

  override fun visitYieldExpression(node: UYieldExpression): Boolean {
    return visitors.all { it.visitYieldExpression(node) }
  }

  override fun visitContinueExpression(node: UContinueExpression): Boolean {
    return visitors.all { it.visitContinueExpression(node) }
  }

  override fun visitThrowExpression(node: UThrowExpression): Boolean {
    return visitors.all { it.visitThrowExpression(node) }
  }

  override fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean {
    return visitors.all { it.visitArrayAccessExpression(node) }
  }

  override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
    return visitors.all { it.visitCallableReferenceExpression(node) }
  }

  override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
    return visitors.all { it.visitClassLiteralExpression(node) }
  }

  override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
    return visitors.all { it.visitLambdaExpression(node) }
  }

  override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
    return visitors.all { it.visitObjectLiteralExpression(node) }
  }
}