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

import com.intellij.psi.PsiIfStatement
import com.intellij.psi.impl.source.tree.ChildRole
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UIfExpression

@ApiStatus.Internal
class JavaUIfExpression(
  override val sourcePsi: PsiIfStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UIfExpression {
  override val condition: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.condition, this) }
  override val thenExpression: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.thenBranch, this) }
  override val elseExpression: UExpression by lazyPub { JavaConverter.convertOrEmpty(sourcePsi.elseBranch, this) }

  override val isTernary: Boolean
    get() = false

  override val ifIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.IF_KEYWORD), this)

  override val elseIdentifier: UIdentifier?
    get() = sourcePsi.getChildByRole(ChildRole.ELSE_KEYWORD)?.let { UIdentifier(it, this) }
}
