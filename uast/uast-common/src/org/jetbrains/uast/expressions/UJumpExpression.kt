// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

/**
 * Represents jump expression (break / continue / yield) with an optional label
 */
interface UJumpExpression : UExpression {
  /**
   * Returns the expression label, or null if the label is not specified.
   */
  val label: String?
}