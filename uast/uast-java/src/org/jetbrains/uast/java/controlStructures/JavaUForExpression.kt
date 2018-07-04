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
package org.jetbrains.uast.java

import com.intellij.psi.PsiForStatement
import com.intellij.psi.impl.source.tree.ChildRole
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.UIdentifier

class JavaUForExpression(
  override val psi: PsiForStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UForExpression {
  override val declaration: UExpression? by lz { psi.initialization?.let { JavaConverter.convertStatement(it, this) } }
  override val condition: UExpression? by lz { psi.condition?.let { JavaConverter.convertExpression(it, this) } }
  override val update: UExpression? by lz { psi.update?.let { JavaConverter.convertStatement(it, this) } }
  override val body: UExpression by lz { JavaConverter.convertOrEmpty(psi.body, this) }

  override val forIdentifier: UIdentifier
    get() = UIdentifier(psi.getChildByRole(ChildRole.FOR_KEYWORD), this)
}