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
  fun visitDeclaration(node: UDeclaration) = visitElement(node)
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
  fun visitExpression(node: UExpression) = visitElement(node)

  fun visitLabeledExpression(node: ULabeledExpression) = visitExpression(node)
  fun visitDeclarationsExpression(node: UDeclarationsExpression) = visitExpression(node)
  fun visitBlockExpression(node: UBlockExpression) = visitExpression(node)
  fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) = visitExpression(node)
  fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) = visitExpression(node)
  fun visitTypeReferenceExpression(node: UTypeReferenceExpression) = visitExpression(node)
  fun visitCallExpression(node: UCallExpression) = visitExpression(node)
  fun visitBinaryExpression(node: UBinaryExpression) = visitExpression(node)
  fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType) = visitExpression(node)
  fun visitPolyadicExpression(node: UPolyadicExpression) = visitExpression(node)
  fun visitParenthesizedExpression(node: UParenthesizedExpression) = visitExpression(node)
  fun visitUnaryExpression(node: UUnaryExpression) = visitExpression(node)
  fun visitPrefixExpression(node: UPrefixExpression) = visitExpression(node)
  fun visitPostfixExpression(node: UPostfixExpression) = visitExpression(node)
  fun visitExpressionList(node: UExpressionList) = visitExpression(node)
  fun visitIfExpression(node: UIfExpression) = visitExpression(node)
  fun visitSwitchExpression(node: USwitchExpression) = visitExpression(node)
  fun visitSwitchClauseExpression(node: USwitchClauseExpression) = visitExpression(node)
  fun visitWhileExpression(node: UWhileExpression) = visitExpression(node)
  fun visitDoWhileExpression(node: UDoWhileExpression) = visitExpression(node)
  fun visitForExpression(node: UForExpression) = visitExpression(node)
  fun visitForEachExpression(node: UForEachExpression) = visitExpression(node)
  fun visitTryExpression(node: UTryExpression) = visitExpression(node)
  fun visitCatchClause(node: UCatchClause) = visitElement(node)
  fun visitLiteralExpression(node: ULiteralExpression) = visitExpression(node)
  fun visitThisExpression(node: UThisExpression) = visitExpression(node)
  fun visitSuperExpression(node: USuperExpression) = visitExpression(node)
  fun visitReturnExpression(node: UReturnExpression) = visitExpression(node)
  fun visitBreakExpression(node: UBreakExpression) = visitExpression(node)
  fun visitContinueExpression(node: UContinueExpression) = visitExpression(node)
  fun visitThrowExpression(node: UThrowExpression) = visitExpression(node)
  fun visitArrayAccessExpression(node: UArrayAccessExpression) = visitExpression(node)
  fun visitCallableReferenceExpression(node: UCallableReferenceExpression) = visitExpression(node)
  fun visitClassLiteralExpression(node: UClassLiteralExpression) = visitExpression(node)
  fun visitLambdaExpression(node: ULambdaExpression) = visitExpression(node)
  fun visitObjectLiteralExpression(node: UObjectLiteralExpression) = visitExpression(node)

  // After

  fun afterVisitElement(node: UElement) {}

  fun afterVisitFile(node: UFile) {
    afterVisitElement(node)
  }

  fun afterVisitImportStatement(node: UImportStatement) {
    afterVisitElement(node)
  }

  fun afterVisitDeclaration(node: UDeclaration) {
    afterVisitElement(node)
  }

  fun afterVisitClass(node: UClass) {
    afterVisitDeclaration(node)
  }

  fun afterVisitInitializer(node: UClassInitializer) {
    afterVisitDeclaration(node)
  }

  fun afterVisitMethod(node: UMethod) {
    afterVisitDeclaration(node)
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
  fun afterVisitExpression(node: UExpression) {
    afterVisitElement(node)
  }

  fun afterVisitLabeledExpression(node: ULabeledExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitDeclarationsExpression(node: UDeclarationsExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitBlockExpression(node: UBlockExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitTypeReferenceExpression(node: UTypeReferenceExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitCallExpression(node: UCallExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitBinaryExpression(node: UBinaryExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitBinaryExpressionWithType(node: UBinaryExpressionWithType) {
    afterVisitExpression(node)
  }

  fun afterVisitParenthesizedExpression(node: UParenthesizedExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitUnaryExpression(node: UUnaryExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitPrefixExpression(node: UPrefixExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitPostfixExpression(node: UPostfixExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitExpressionList(node: UExpressionList) {
    afterVisitExpression(node)
  }

  fun afterVisitIfExpression(node: UIfExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitSwitchExpression(node: USwitchExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitSwitchClauseExpression(node: USwitchClauseExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitWhileExpression(node: UWhileExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitDoWhileExpression(node: UDoWhileExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitForExpression(node: UForExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitForEachExpression(node: UForEachExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitTryExpression(node: UTryExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitCatchClause(node: UCatchClause) {
    afterVisitElement(node)
  }

  fun afterVisitLiteralExpression(node: ULiteralExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitThisExpression(node: UThisExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitSuperExpression(node: USuperExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitReturnExpression(node: UReturnExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitBreakExpression(node: UBreakExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitContinueExpression(node: UContinueExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitThrowExpression(node: UThrowExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitArrayAccessExpression(node: UArrayAccessExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitCallableReferenceExpression(node: UCallableReferenceExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitClassLiteralExpression(node: UClassLiteralExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitLambdaExpression(node: ULambdaExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitObjectLiteralExpression(node: UObjectLiteralExpression) {
    afterVisitExpression(node)
  }

  fun afterVisitPolyadicExpression(node: UPolyadicExpression) {
    afterVisitExpression(node)
  }
}

abstract class AbstractUastVisitor : UastVisitor {
  override fun visitElement(node: UElement): Boolean = false

}

object EmptyUastVisitor : AbstractUastVisitor()