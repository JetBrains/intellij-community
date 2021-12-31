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

import com.intellij.psi.PsiContinueStatement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UContinueExpression
import org.jetbrains.uast.UElement

@ApiStatus.Internal
class JavaUContinueExpression(
  override val sourcePsi: PsiContinueStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UContinueExpression {
  override val label: String?
    get() = sourcePsi.labelIdentifier?.text

  override val jumpTarget: UElement? by lz {
    sourcePsi.findContinuedStatement().takeIf { it !== sourcePsi }?.let { JavaConverter.convertStatement(it, null) }
  }
}
