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

@file:JvmName("UastExpressionUtils")

package org.jetbrains.uast.util

import org.jetbrains.uast.*

fun UElement.isConstructorCall(): Boolean = (this as? UCallExpression)?.kind == UastCallKind.CONSTRUCTOR_CALL

fun UElement.isMethodCall(): Boolean = (this as? UCallExpression)?.kind == UastCallKind.METHOD_CALL

fun UElement.isNewArray(): Boolean = isNewArrayWithDimensions() || isNewArrayWithInitializer()

fun UElement.isNewArrayWithDimensions(): Boolean = (this as? UCallExpression)?.kind == UastCallKind.NEW_ARRAY_WITH_DIMENSIONS

fun UElement.isNewArrayWithInitializer(): Boolean = (this as? UCallExpression)?.kind == UastCallKind.NEW_ARRAY_WITH_INITIALIZER

fun UElement.isArrayInitializer(): Boolean = (this as? UCallExpression)?.kind == UastCallKind.NESTED_ARRAY_INITIALIZER

fun UElement.isTypeCast(): Boolean = (this as? UBinaryExpressionWithType)?.operationKind is UastBinaryExpressionWithTypeKind.TypeCast

fun UElement.isInstanceCheck(): Boolean = (this as? UBinaryExpressionWithType)?.operationKind is UastBinaryExpressionWithTypeKind.InstanceCheck

fun UElement.isAssignment(): Boolean = (this as? UBinaryExpression)?.operator is UastBinaryOperator.AssignOperator

fun UVariable.isResourceVariable(): Boolean = uastParent is UTryExpression
