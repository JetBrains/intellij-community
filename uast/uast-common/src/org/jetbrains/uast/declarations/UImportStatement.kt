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

import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents an import statement.
 */
interface UImportStatement : UResolvable, UElement {
  /**
   * Returns true if the statement is an import-on-demand (star-import) statement.
   */
  val isOnDemand: Boolean

  /**
   * Returns the reference to the imported element.
   */
  val importReference: UElement?

  override fun asLogString(): String = log("isOnDemand = $isOnDemand")

  override fun asRenderString(): String = "import " + (importReference?.asRenderString() ?: "<error>")

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitImportStatement(this)) return
    visitor.afterVisitImportStatement(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitImportStatement(this, data)
}