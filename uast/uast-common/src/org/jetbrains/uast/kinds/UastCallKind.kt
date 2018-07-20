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
 * Kinds of [UCallExpression].
 */
open class UastCallKind(val name: String) {
  companion object {
    @JvmField
    val METHOD_CALL: UastCallKind = UastCallKind("method_call")

    @JvmField
    val CONSTRUCTOR_CALL: UastCallKind = UastCallKind("constructor_call")

    @JvmField
    val NEW_ARRAY_WITH_DIMENSIONS: UastCallKind = UastCallKind("new_array_with_dimensions")

    /**
     * Initializer parts are available in call expression as value arguments.
     * [NEW_ARRAY_WITH_INITIALIZER] is a top-level initializer. In case of multi-dimensional arrays, inner initializers
     *  have type of [NESTED_ARRAY_INITIALIZER].
     */
    @JvmField
    val NEW_ARRAY_WITH_INITIALIZER: UastCallKind = UastCallKind("new_array_with_initializer")

    @JvmField
    val NESTED_ARRAY_INITIALIZER: UastCallKind = UastCallKind("array_initializer")
  }

  override fun toString(): String {
    return "UastCallKind(name='$name')"
  }
}