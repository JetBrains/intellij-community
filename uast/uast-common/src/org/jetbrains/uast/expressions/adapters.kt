// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("unused")

package org.jetbrains.uast

import org.jetbrains.uast.expressions.UInjectionHost

/*
 * Mocks of the UAST expressions interfaces.
 *
 * Can be useful for UAST plugins written in Kotlin and may be the only way to implement
 * needed interfaces in other JVM-languages such as Scala, where JVM clashes happen
 * when trying to inherit from some UAST interfaces.
 *
 * Provides:
 *  - Elimination of some possible JVM clashes
 *  - Inherited default implementations from UAST interfaces
 *  - Kotlin delegation mechanism which helps implement PSI interfaces by some delegate
 */

abstract class UArrayAccessExpressionAdapter : UArrayAccessExpression

abstract class UBinaryExpressionAdapter : UBinaryExpression

abstract class UBinaryExpressionWithTypeAdapter : UBinaryExpressionWithType

abstract class UBlockExpressionAdapter : UBlockExpression

abstract class UBreakExpressionAdapter : UBreakExpression

abstract class UCallableReferenceExpressionAdapter : UCallableReferenceExpression

abstract class UCallExpressionAdapter : UCallExpression

abstract class UClassLiteralExpressionAdapter : UClassLiteralExpression

abstract class UContinueExpressionAdapter : UContinueExpression

abstract class UDeclarationsExpressionAdapter : UDeclarationsExpression

abstract class UExpressionListAdapter : UExpressionList

abstract class UInjectionHostAdapter : UInjectionHost

abstract class UInstanceExpressionAdapter : UInstanceExpression

abstract class UJumpExpressionAdapter : UJumpExpression

abstract class ULabeledExpressionAdapter : ULabeledExpression

abstract class ULambdaExpressionAdapter : ULambdaExpression

abstract class ULiteralExpressionAdapter : ULiteralExpression

abstract class UNamedExpressionAdapter : UNamedExpression

abstract class UObjectLiteralExpressionAdapter : UObjectLiteralExpression

abstract class UParenthesizedExpressionAdapter : UParenthesizedExpression

abstract class UPolyadicExpressionAdapter : UPolyadicExpression

abstract class UQualifiedReferenceExpressionAdapter : UQualifiedReferenceExpression

abstract class UReferenceExpressionAdapter : UReferenceExpression

abstract class UReturnExpressionAdapter : UReturnExpression

abstract class USimpleNameReferenceExpressionAdapter : USimpleNameReferenceExpression

abstract class USuperExpressionAdapter : USuperExpression

abstract class UThisExpressionAdapter : UThisExpression

abstract class UThrowExpressionAdapter : UThrowExpression

abstract class UTypeReferenceExpressionAdapter : UTypeReferenceExpression

abstract class UUnaryExpressionAdapter : UUnaryExpression

abstract class UPrefixExpressionAdapter : UPrefixExpression

abstract class UPostfixExpressionAdapter : UPostfixExpression

abstract class UYieldExpressionAdapter : UYieldExpression