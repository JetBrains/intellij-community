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

import com.intellij.psi.PsiForeachStatement
import com.intellij.psi.impl.source.tree.ChildRole
import org.jetbrains.uast.*

class JavaUForEachExpression(
  override val psi: PsiForeachStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UForEachExpression {
  override val variable: UParameter
    get() = JavaUParameter(psi.iterationParameter, this)

  override val iteratedValue: UExpression by lz { JavaConverter.convertOrEmpty(psi.iteratedValue, this) }
  override val body: UExpression by lz { JavaConverter.convertOrEmpty(psi.body, this) }

  override val forIdentifier: UIdentifier
    get() = UIdentifier(psi.getChildByRole(ChildRole.FOR_KEYWORD), this)
}