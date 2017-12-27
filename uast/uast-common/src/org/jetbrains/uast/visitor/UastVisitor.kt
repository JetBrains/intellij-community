/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.visitor

import org.jetbrains.uast.*

interface UastVisitor {
  fun visitElement(node: UElement): Boolean

  fun visitFile(node: UFile): Boolean = visitElement(node)
  fun visitImportStatement(node: UImportStatement): Boolean = visitElement(node)
  fun visitClass(node: UClass): Boolean = visitElement(node)
  fun visitInitializer(node: UClassInitializer): Boolean = visitElement(node)
  fun visitMethod(node: UMethod): Boolean = visitElement(node)
  fun visitVariable(node: UVariable): Boolean = visitElement(node)
  fun visitParameter(node: UParameter): Boolean = visitVariable(node)
  fun visitField(node: UField): Boolean = visitVariable(node)
  fun visitLocalVariable(node: ULocalVariable): Boolean = visitVariable(node)
  fun visitEnumConstant(node: UEnumConstant): Boolean = visitField(node)

  fun visitAnnotation(node: UAnnotation): Boolean = visitElement(node)

  // Expressions
  fun visitLabeledExpression(node: ULabeledExpression) = visitElement(node)

  fun visitDeclarationsExpression(node: UDeclarationsExpression) = visitElement(node)
  fun visitBlockExpression(node: UBlockExpression) = visitElement(node)
  fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) = visitElement(node)
  fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) = visitElement(node)
  fun visitTypeReferenceExpression(node: UTypeReferenceExpression) = visitElement(node)
  fun visitCallExpression(node: UCallExpression) = visitElement(node)
  fun visitBinaryExpression(node: UBinaryExpression) = visitElement(node)
  fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType) = visitElement(node)
  fun visitPolyadicExpression(node: UPolyadicExpression) = visitElement(node)
  fun visitParenthesizedExpression(node: UParenthesizedExpression) = visitElement(node)
  fun visitUnaryExpression(node: UUnaryExpression) = visitElement(node)
  fun visitPrefixExpression(node: UPrefixExpression) = visitElement(node)
  fun visitPostfixExpression(node: UPostfixExpression) = visitElement(node)
  fun visitExpressionList(node: UExpressionList) = visitElement(node)
  fun visitIfExpression(node: UIfExpression) = visitElement(node)
  fun visitSwitchExpression(node: USwitchExpression) = visitElement(node)
  fun visitSwitchClauseExpression(node: USwitchClauseExpression) = visitElement(node)
  fun visitWhileExpression(node: UWhileExpression) = visitElement(node)
  fun visitDoWhileExpression(node: UDoWhileExpression) = visitElement(node)
  fun visitForExpression(node: UForExpression) = visitElement(node)
  fun visitForEachExpression(node: UForEachExpression) = visitElement(node)
  fun visitTryExpression(node: UTryExpression) = visitElement(node)
  fun visitCatchClause(node: UCatchClause) = visitElement(node)
  fun visitLiteralExpression(node: ULiteralExpression) = visitElement(node)
  fun visitThisExpression(node: UThisExpression) = visitElement(node)
  fun visitSuperExpression(node: USuperExpression) = visitElement(node)
  fun visitReturnExpression(node: UReturnExpression) = visitElement(node)
  fun visitBreakExpression(node: UBreakExpression) = visitElement(node)
  fun visitContinueExpression(node: UContinueExpression) = visitElement(node)
  fun visitThrowExpression(node: UThrowExpression) = visitElement(node)
  fun visitArrayAccessExpression(node: UArrayAccessExpression) = visitElement(node)
  fun visitCallableReferenceExpression(node: UCallableReferenceExpression) = visitElement(node)
  fun visitClassLiteralExpression(node: UClassLiteralExpression) = visitElement(node)
  fun visitLambdaExpression(node: ULambdaExpression) = visitElement(node)
  fun visitObjectLiteralExpression(node: UObjectLiteralExpression) = visitElement(node)

  // After

  fun afterVisitElement(node: UElement) {}

  fun afterVisitFile(node: UFile) {
    afterVisitElement(node)
  }

  fun afterVisitImportStatement(node: UImportStatement) {
    afterVisitElement(node)
  }

  fun afterVisitClass(node: UClass) {
    afterVisitElement(node)
  }

  fun afterVisitInitializer(node: UClassInitializer) {
    afterVisitElement(node)
  }

  fun afterVisitMethod(node: UMethod) {
    afterVisitElement(node)
  }

  fun afterVisitVariable(node: UVariable) {
    afterVisitElement(node)
  }

  fun afterVisitParameter(node: UParameter) {
    afterVisitVariable(node)
  }

  fun afterVisitField(node: UField) {
    afterVisitVariable(node)
  }

  fun afterVisitLocalVariable(node: ULocalVariable) {
    afterVisitVariable(node)
  }

  fun afterVisitEnumConstant(node: UEnumConstant) {
    afterVisitField(node)
  }

  fun afterVisitAnnotation(node: UAnnotation) {
    afterVisitElement(node)
  }

  // Expressions
  fun afterVisitLabeledExpression(node: ULabeledExpression) {
    afterVisitElement(node)
  }

  fun afterVisitDeclarationsExpression(node: UDeclarationsExpression) {
    afterVisitElement(node)
  }

  fun afterVisitBlockExpression(node: UBlockExpression) {
    afterVisitElement(node)
  }

  fun afterVisitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
    afterVisitElement(node)
  }

  fun afterVisitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
    afterVisitElement(node)
  }

  fun afterVisitTypeReferenceExpression(node: UTypeReferenceExpression) {
    afterVisitElement(node)
  }

  fun afterVisitCallExpression(node: UCallExpression) {
    afterVisitElement(node)
  }

  fun afterVisitBinaryExpression(node: UBinaryExpression) {
    afterVisitElement(node)
  }

  fun afterVisitBinaryExpressionWithType(node: UBinaryExpressionWithType) {
    afterVisitElement(node)
  }

  fun afterVisitParenthesizedExpression(node: UParenthesizedExpression) {
    afterVisitElement(node)
  }

  fun afterVisitUnaryExpression(node: UUnaryExpression) {
    afterVisitElement(node)
  }

  fun afterVisitPrefixExpression(node: UPrefixExpression) {
    afterVisitElement(node)
  }

  fun afterVisitPostfixExpression(node: UPostfixExpression) {
    afterVisitElement(node)
  }

  fun afterVisitExpressionList(node: UExpressionList) {
    afterVisitElement(node)
  }

  fun afterVisitIfExpression(node: UIfExpression) {
    afterVisitElement(node)
  }

  fun afterVisitSwitchExpression(node: USwitchExpression) {
    afterVisitElement(node)
  }

  fun afterVisitSwitchClauseExpression(node: USwitchClauseExpression) {
    afterVisitElement(node)
  }

  fun afterVisitWhileExpression(node: UWhileExpression) {
    afterVisitElement(node)
  }

  fun afterVisitDoWhileExpression(node: UDoWhileExpression) {
    afterVisitElement(node)
  }

  fun afterVisitForExpression(node: UForExpression) {
    afterVisitElement(node)
  }

  fun afterVisitForEachExpression(node: UForEachExpression) {
    afterVisitElement(node)
  }

  fun afterVisitTryExpression(node: UTryExpression) {
    afterVisitElement(node)
  }

  fun afterVisitCatchClause(node: UCatchClause) {
    afterVisitElement(node)
  }

  fun afterVisitLiteralExpression(node: ULiteralExpression) {
    afterVisitElement(node)
  }

  fun afterVisitThisExpression(node: UThisExpression) {
    afterVisitElement(node)
  }

  fun afterVisitSuperExpression(node: USuperExpression) {
    afterVisitElement(node)
  }

  fun afterVisitReturnExpression(node: UReturnExpression) {
    afterVisitElement(node)
  }

  fun afterVisitBreakExpression(node: UBreakExpression) {
    afterVisitElement(node)
  }

  fun afterVisitContinueExpression(node: UContinueExpression) {
    afterVisitElement(node)
  }

  fun afterVisitThrowExpression(node: UThrowExpression) {
    afterVisitElement(node)
  }

  fun afterVisitArrayAccessExpression(node: UArrayAccessExpression) {
    afterVisitElement(node)
  }

  fun afterVisitCallableReferenceExpression(node: UCallableReferenceExpression) {
    afterVisitElement(node)
  }

  fun afterVisitClassLiteralExpression(node: UClassLiteralExpression) {
    afterVisitElement(node)
  }

  fun afterVisitLambdaExpression(node: ULambdaExpression) {
    afterVisitElement(node)
  }

  fun afterVisitObjectLiteralExpression(node: UObjectLiteralExpression) {
    afterVisitElement(node)
  }

  fun afterVisitPolyadicExpression(node: UPolyadicExpression) {
    afterVisitElement(node)
  }
}

interface UastVisitorEx : UastVisitor {

  fun visitDeclaration(node: UDeclaration) = visitElement(node)
  override fun visitClass(node: UClass): Boolean = visitDeclaration(node)
  override fun visitInitializer(node: UClassInitializer): Boolean = visitDeclaration(node)
  override fun visitMethod(node: UMethod): Boolean = visitDeclaration(node)
  override fun visitVariable(node: UVariable): Boolean = visitDeclaration(node)
  override fun visitEnumConstant(node: UEnumConstant): Boolean = visitExpression(node)
  fun visitExpression(node: UExpression) = visitElement(node)
  override fun visitLabeledExpression(node: ULabeledExpression) = visitExpression(node)
  override fun visitDeclarationsExpression(node: UDeclarationsExpression) = visitExpression(node)
  override fun visitBlockExpression(node: UBlockExpression) = visitExpression(node)
  override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) = visitExpression(node)
  override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) = visitExpression(node)
  override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) = visitExpression(node)
  override fun visitCallExpression(node: UCallExpression) = visitExpression(node)
  override fun visitBinaryExpression(node: UBinaryExpression) = visitExpression(node)
  override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType) = visitExpression(node)
  override fun visitPolyadicExpression(node: UPolyadicExpression) = visitExpression(node)
  override fun visitParenthesizedExpression(node: UParenthesizedExpression) = visitExpression(node)
  override fun visitUnaryExpression(node: UUnaryExpression) = visitExpression(node)
  override fun visitPrefixExpression(node: UPrefixExpression) = visitExpression(node)
  override fun visitPostfixExpression(node: UPostfixExpression) = visitExpression(node)
  override fun visitExpressionList(node: UExpressionList) = visitExpression(node)
  override fun visitIfExpression(node: UIfExpression) = visitExpression(node)
  override fun visitSwitchExpression(node: USwitchExpression) = visitExpression(node)
  override fun visitSwitchClauseExpression(node: USwitchClauseExpression) = visitExpression(node)
  override fun visitWhileExpression(node: UWhileExpression) = visitExpression(node)
  override fun visitDoWhileExpression(node: UDoWhileExpression) = visitExpression(node)
  override fun visitForExpression(node: UForExpression) = visitExpression(node)
  override fun visitForEachExpression(node: UForEachExpression) = visitExpression(node)
  override fun visitTryExpression(node: UTryExpression) = visitExpression(node)
  override fun visitLiteralExpression(node: ULiteralExpression) = visitExpression(node)
  override fun visitThisExpression(node: UThisExpression) = visitExpression(node)
  override fun visitSuperExpression(node: USuperExpression) = visitExpression(node)
  override fun visitReturnExpression(node: UReturnExpression) = visitExpression(node)
  override fun visitBreakExpression(node: UBreakExpression) = visitExpression(node)
  override fun visitContinueExpression(node: UContinueExpression) = visitExpression(node)
  override fun visitThrowExpression(node: UThrowExpression) = visitExpression(node)
  override fun visitArrayAccessExpression(node: UArrayAccessExpression) = visitExpression(node)
  override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) = visitExpression(node)
  override fun visitClassLiteralExpression(node: UClassLiteralExpression) = visitExpression(node)
  override fun visitLambdaExpression(node: ULambdaExpression) = visitExpression(node)
  override fun visitObjectLiteralExpression(node: UObjectLiteralExpression) = visitExpression(node)

  fun afterVisitDeclaration(node: UDeclaration) {
    afterVisitElement(node)
  }

  override fun afterVisitClass(node: UClass) {
    afterVisitDeclaration(node)
  }

  override fun afterVisitInitializer(node: UClassInitializer) {
    afterVisitDeclaration(node)
  }

  override fun afterVisitMethod(node: UMethod) {
    afterVisitDeclaration(node)
  }

  override fun afterVisitVariable(node: UVariable) {
    afterVisitDeclaration(node)
  }

  override fun afterVisitEnumConstant(node: UEnumConstant) {
    afterVisitExpression(node)
  }

  // Expressions
  fun afterVisitExpression(node: UExpression) {
    afterVisitElement(node)
  }

  override fun afterVisitLabeledExpression(node: ULabeledExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitDeclarationsExpression(node: UDeclarationsExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitBlockExpression(node: UBlockExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitTypeReferenceExpression(node: UTypeReferenceExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitCallExpression(node: UCallExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitBinaryExpression(node: UBinaryExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitBinaryExpressionWithType(node: UBinaryExpressionWithType) {
    afterVisitExpression(node)
  }

  override fun afterVisitParenthesizedExpression(node: UParenthesizedExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitUnaryExpression(node: UUnaryExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitPrefixExpression(node: UPrefixExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitPostfixExpression(node: UPostfixExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitExpressionList(node: UExpressionList) {
    afterVisitExpression(node)
  }

  override fun afterVisitIfExpression(node: UIfExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitSwitchExpression(node: USwitchExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitSwitchClauseExpression(node: USwitchClauseExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitWhileExpression(node: UWhileExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitDoWhileExpression(node: UDoWhileExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitForExpression(node: UForExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitForEachExpression(node: UForEachExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitTryExpression(node: UTryExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitLiteralExpression(node: ULiteralExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitThisExpression(node: UThisExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitSuperExpression(node: USuperExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitReturnExpression(node: UReturnExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitBreakExpression(node: UBreakExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitContinueExpression(node: UContinueExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitThrowExpression(node: UThrowExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitArrayAccessExpression(node: UArrayAccessExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitCallableReferenceExpression(node: UCallableReferenceExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitClassLiteralExpression(node: UClassLiteralExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitLambdaExpression(node: ULambdaExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitObjectLiteralExpression(node: UObjectLiteralExpression) {
    afterVisitExpression(node)
  }

  override fun afterVisitPolyadicExpression(node: UPolyadicExpression) {
    afterVisitExpression(node)
  }
}

abstract class AbstractUastVisitor : UastVisitorEx {
  override fun visitElement(node: UElement): Boolean = false

}

object EmptyUastVisitor : AbstractUastVisitor()