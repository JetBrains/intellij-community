// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("unused")

package org.jetbrains.uast

/*
 * Mocks of the UAST control-structures interfaces.
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

abstract class UWhileExpressionAdapter : UWhileExpression

abstract class UDoWhileExpressionAdapter : UDoWhileExpression

abstract class UForEachExpressionAdapter : UForEachExpression

abstract class UForExpressionAdapter : UForExpression

abstract class UIfExpressionAdapter : UIfExpression

abstract class ULoopExpressionAdapter : ULoopExpression

abstract class USwitchExpressionAdapter : USwitchExpression

abstract class USwitchClauseExpressionAdapter : USwitchClauseExpression

abstract class USwitchClauseExpressionWithBodyAdapter : USwitchClauseExpressionWithBody

abstract class UTryExpressionAdapter : UTryExpression

abstract class UCatchExpressionAdapter : UCatchClause