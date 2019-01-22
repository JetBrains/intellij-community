// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.evaluation

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.uast.*
import org.jetbrains.uast.values.UUndeterminedValue
import org.jetbrains.uast.visitor.UastTypedVisitor

internal class DepthLimitingEvaluatorVisitor(val depthLimit: kotlin.Int,
                                             delegator: (org.jetbrains.uast.evaluation.DepthLimitingEvaluatorVisitor) -> org.jetbrains.uast.visitor.UastTypedVisitor<org.jetbrains.uast.evaluation.UEvaluationState, org.jetbrains.uast.evaluation.UEvaluationInfo>) : UastTypedVisitor<UEvaluationState, UEvaluationInfo> {

  private val delegate = delegator(this)

  private var depth: Int = 0

  private inline fun <T : UElement> wrapCall(node: T,
                                             data: UEvaluationState,
                                             delegateCall: (T, UEvaluationState) -> UEvaluationInfo): UEvaluationInfo {
    try {
      depth++
      if (depth > depthLimit) {
        LOG.info("evaluation depth exceeded $depth > $depthLimit for '$node' in '${node.sourcePsi?.containingFile?.name}'")
        return UUndeterminedValue to data
      }
      return delegateCall(node, data)
    }
    finally {
      depth--
    }
  }

  override fun visitElement(node: UElement, data: UEvaluationState): UEvaluationInfo = wrapCall(node, data, delegate::visitElement)

  override fun visitFile(node: UFile, data: UEvaluationState): UEvaluationInfo = wrapCall(node, data, delegate::visitFile)

  override fun visitImportStatement(node: UImportStatement, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitImportStatement)

  override fun visitAnnotation(node: UAnnotation, data: UEvaluationState): UEvaluationInfo = wrapCall(node, data, delegate::visitAnnotation)

  override fun visitCatchClause(node: UCatchClause, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitCatchClause)

  override fun visitDeclaration(node: UDeclaration, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitDeclaration)

  override fun visitClass(node: UClass, data: UEvaluationState): UEvaluationInfo = wrapCall(node, data, delegate::visitClass)

  override fun visitMethod(node: UMethod, data: UEvaluationState): UEvaluationInfo = wrapCall(node, data, delegate::visitMethod)

  override fun visitClassInitializer(node: UClassInitializer, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitClassInitializer)

  override fun visitVariable(node: UVariable, data: UEvaluationState): UEvaluationInfo = wrapCall(node, data, delegate::visitVariable)

  override fun visitParameter(node: UParameter, data: UEvaluationState): UEvaluationInfo = wrapCall(node, data, delegate::visitParameter)

  override fun visitField(node: UField, data: UEvaluationState): UEvaluationInfo = wrapCall(node, data, delegate::visitField)

  override fun visitLocalVariable(node: ULocalVariable, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitLocalVariable)

  override fun visitEnumConstantExpression(node: UEnumConstant, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitEnumConstantExpression)

  override fun visitExpression(node: UExpression, data: UEvaluationState): UEvaluationInfo = wrapCall(node, data, delegate::visitExpression)

  override fun visitLabeledExpression(node: ULabeledExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitLabeledExpression)

  override fun visitDeclarationsExpression(node: UDeclarationsExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitDeclarationsExpression)

  override fun visitBlockExpression(node: UBlockExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitBlockExpression)

  override fun visitTypeReferenceExpression(node: UTypeReferenceExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitTypeReferenceExpression)

  override fun visitExpressionList(node: UExpressionList, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitExpressionList)

  override fun visitLiteralExpression(node: ULiteralExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitLiteralExpression)

  override fun visitThisExpression(node: UThisExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitThisExpression)

  override fun visitSuperExpression(node: USuperExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitSuperExpression)

  override fun visitArrayAccessExpression(node: UArrayAccessExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitArrayAccessExpression)

  override fun visitClassLiteralExpression(node: UClassLiteralExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitClassLiteralExpression)

  override fun visitLambdaExpression(node: ULambdaExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitLambdaExpression)

  override fun visitPolyadicExpression(node: UPolyadicExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitPolyadicExpression)

  override fun visitCallExpression(node: UCallExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitCallExpression)

  override fun visitObjectLiteralExpression(node: UObjectLiteralExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitObjectLiteralExpression)

  override fun visitBinaryExpression(node: UBinaryExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitBinaryExpression)

  override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitBinaryExpressionWithType)

  override fun visitParenthesizedExpression(node: UParenthesizedExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitParenthesizedExpression)

  override fun visitUnaryExpression(node: UUnaryExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitUnaryExpression)

  override fun visitPrefixExpression(node: UPrefixExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitPrefixExpression)

  override fun visitPostfixExpression(node: UPostfixExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitPostfixExpression)

  override fun visitReferenceExpression(node: UReferenceExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitReferenceExpression)

  override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitQualifiedReferenceExpression)

  override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitSimpleNameReferenceExpression)

  override fun visitCallableReferenceExpression(node: UCallableReferenceExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitCallableReferenceExpression)

  override fun visitIfExpression(node: UIfExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitIfExpression)

  override fun visitSwitchExpression(node: USwitchExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitSwitchExpression)

  override fun visitSwitchClauseExpression(node: USwitchClauseExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitSwitchClauseExpression)

  override fun visitTryExpression(node: UTryExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitTryExpression)

  override fun visitReturnExpression(node: UReturnExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitReturnExpression)

  override fun visitBreakExpression(node: UBreakExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitBreakExpression)

  override fun visitContinueExpression(node: UContinueExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitContinueExpression)

  override fun visitThrowExpression(node: UThrowExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitThrowExpression)

  override fun visitLoopExpression(node: ULoopExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitLoopExpression)

  override fun visitWhileExpression(node: UWhileExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitWhileExpression)

  override fun visitDoWhileExpression(node: UDoWhileExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitDoWhileExpression)

  override fun visitForExpression(node: UForExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitForExpression)

  override fun visitForEachExpression(node: UForEachExpression, data: UEvaluationState): UEvaluationInfo =
    wrapCall(node, data, delegate::visitForEachExpression)
}

private val LOG = Logger.getInstance(DepthLimitingEvaluatorVisitor::class.java)
