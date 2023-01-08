// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

/**
 * Represents jump expression (break / continue / yield / return) with an optional label
 */
interface UJumpExpression : UExpression {
  /**
   * Returns the expression label, or null if the label is not specified.
   */
  val label: String?

  /**
   * The wrapping element (operator body, function declaration) from where the control flow will get out after execution of this jump expression
   */
  val jumpTarget: UElement? get() = null
}