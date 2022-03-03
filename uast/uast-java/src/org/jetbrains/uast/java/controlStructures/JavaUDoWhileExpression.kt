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

import com.intellij.psi.PsiDoWhileStatement
import com.intellij.psi.impl.source.tree.ChildRole
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIdentifier

@ApiStatus.Internal
class JavaUDoWhileExpression(
  override val sourcePsi: PsiDoWhileStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UDoWhileExpression {
  override val condition: UExpression by lz { JavaConverter.convertOrEmpty(sourcePsi.condition, this) }
  override val body: UExpression by lz { JavaConverter.convertOrEmpty(sourcePsi.body, this) }

  override val doIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.DO_KEYWORD), this)
  override val whileIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.WHILE_KEYWORD), this)
}
