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
  fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean = visitExpression(node)
  fun visitCallExpression(node: UCallExpression): Boolean = visitExpression(node)
  fun visitBinaryExpression(node: UBinaryExpression): Boolean = visitExpression(node)
  fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType): Boolean = visitExpression(node)
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
  fun visitContinueExpression(node: UContinueExpression): Boolean = visitExpression(node)
  fun visitThrowExpression(node: UThrowExpression): Boolean = visitExpression(node)
  fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean = visitExpression(node)
  fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean = visitExpression(node)
  fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean = visitExpression(node)
  fun visitLambdaExpression(node: ULambdaExpression): Boolean = visitExpression(node)
  fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean = visitExpression(node)

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

/**
 * There is a convention in UAST-visitors that visitor will not be passed to children if `visit*` will return true.
 * So make sure that overridden methods returns `true` and please think twice before returning `false` if you are passing implementation to
 * [com.intellij.uast.UastVisitorAdapter].
 */
abstract class AbstractUastNonRecursiveVisitor : UastVisitor {
  override fun visitElement(node: UElement): Boolean = true
}

object EmptyUastVisitor : AbstractUastVisitor()