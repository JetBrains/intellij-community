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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.UIdentifier

@ApiStatus.Internal
class JavaUForExpression(
  override val sourcePsi: PsiForStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UForExpression {
  override val declaration: UExpression? by lazyPub { sourcePsi.initialization?.let { JavaConverter.convertStatement(it, this, UExpression::class.java) } }
  override val condition: UExpression? by lazyPub { sourcePsi.condition?.let { JavaConverter.convertExpression(it, this, UExpression::class.java) } }
  override val update: UExpression? by lazyPub { sourcePsi.update?.let { JavaConverter.convertStatement(it, this, UExpression::class.java) } }
  override val body: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.body, this) }

  override val forIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.FOR_KEYWORD), this)
}
