// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.uast.testFramework.common

import org.jetbrains.uast.UAnchorOwner
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnnotationEx
import org.jetbrains.uast.UAnnotationMethod
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallExpressionEx
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UClassInitializerEx
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UComment
import org.jetbrains.uast.UContinueExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UDeclarationEx
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UEnumConstantEx
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFieldEx
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UInstanceExpression
import org.jetbrains.uast.UJumpExpression
import org.jetbrains.uast.ULabeled
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.ULocalVariableEx
import org.jetbrains.uast.ULoopExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParameterEx
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UVariableEx
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.psi.UElementWithLocation


@JvmField
@Suppress("DEPRECATION")
val allUElementSubtypes: Set<Class<out UElement>> = setOf(
  UAnchorOwner::class.java,
  UAnnotated::class.java,
  UAnnotation::class.java,
  UAnnotationEx::class.java,
  UAnnotationMethod::class.java,
  UAnonymousClass::class.java,
  UArrayAccessExpression::class.java,
  UBinaryExpression::class.java,
  UBinaryExpressionWithType::class.java,
  UBlockExpression::class.java,
  UBreakExpression::class.java,
  UCallExpression::class.java,
  UCallExpressionEx::class.java,
  UCallableReferenceExpression::class.java,
  UCatchClause::class.java,
  UClass::class.java,
  UClassInitializer::class.java,
  UClassInitializerEx::class.java,
  UClassLiteralExpression::class.java,
  UComment::class.java,
  UContinueExpression::class.java,
  UDeclaration::class.java,
  UDeclarationEx::class.java,
  UDeclarationsExpression::class.java,
  UDoWhileExpression::class.java,
  UElement::class.java,
  UElementWithLocation::class.java,
  UEnumConstant::class.java,
  UEnumConstantEx::class.java,
  UExpression::class.java,
  UExpressionList::class.java,
  UField::class.java,
  UFieldEx::class.java,
  UFile::class.java,
  UForEachExpression::class.java,
  UForExpression::class.java,
  UIdentifier::class.java,
  UIfExpression::class.java,
  UImportStatement::class.java,
  UInjectionHost::class.java,
  UInstanceExpression::class.java,
  UJumpExpression::class.java,
  ULabeled::class.java,
  ULabeledExpression::class.java,
  ULambdaExpression::class.java,
  ULiteralExpression::class.java,
  ULocalVariable::class.java,
  ULocalVariableEx::class.java,
  ULoopExpression::class.java,
  UMethod::class.java,
  UNamedExpression::class.java,
  UObjectLiteralExpression::class.java,
  UParameter::class.java,
  UParameterEx::class.java,
  UParenthesizedExpression::class.java,
  UPolyadicExpression::class.java,
  UPostfixExpression::class.java,
  UPrefixExpression::class.java,
  UQualifiedReferenceExpression::class.java,
  UReferenceExpression::class.java,
  UReturnExpression::class.java,
  USimpleNameReferenceExpression::class.java,
  USuperExpression::class.java,
  USwitchClauseExpression::class.java,
  USwitchClauseExpressionWithBody::class.java,
  USwitchExpression::class.java,
  UThisExpression::class.java,
  UThrowExpression::class.java,
  UTryExpression::class.java,
  UTypeReferenceExpression::class.java,
  UUnaryExpression::class.java,
  UVariable::class.java,
  UVariableEx::class.java,
  UWhileExpression::class.java,
  UYieldExpression::class.java,
  UastEmptyExpression::class.java
)