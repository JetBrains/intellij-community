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
package org.jetbrains.uast

/**
 * [UPrefixExpression] operators.
 */
class UastPrefixOperator(override val text: String) : UastOperator {
  companion object {
    @JvmField
    val INC: UastPrefixOperator = UastPrefixOperator("++")

    @JvmField
    val DEC: UastPrefixOperator = UastPrefixOperator("--")

    @JvmField
    val UNARY_MINUS: UastPrefixOperator = UastPrefixOperator("-")

    @JvmField
    val UNARY_PLUS: UastPrefixOperator = UastPrefixOperator("+")

    @JvmField
    val LOGICAL_NOT: UastPrefixOperator = UastPrefixOperator("!")

    @JvmField
    val BITWISE_NOT: UastPrefixOperator = UastPrefixOperator("~")

    @JvmField
    val UNKNOWN: UastPrefixOperator = UastPrefixOperator("<unknown>")
  }

  override fun toString(): String = text
}
