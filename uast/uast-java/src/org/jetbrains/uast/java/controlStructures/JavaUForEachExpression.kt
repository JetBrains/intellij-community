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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUForEachExpression(
  override val sourcePsi: PsiForeachStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UForEachExpression {
  @Deprecated("property may throw exception if foreach doesn't have variable", replaceWith = ReplaceWith("parameter"))
  override val variable: UParameter
    get() = JavaUParameter(sourcePsi.iterationParameter ?: error("Migrate code to $parameter"), this)

  override val parameter: UParameter?
    get() {
      val psiParameter = sourcePsi.iterationParameter ?: return null
      return JavaUParameter(psiParameter, this)
    }

  override val iteratedValue: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.iteratedValue, this) }
  override val body: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.body, this) }

  override val forIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.FOR_KEYWORD), this)
}
