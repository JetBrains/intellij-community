// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java

import com.intellij.psi.PsiBreakStatement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UElement

@ApiStatus.Internal
class JavaUBreakExpression(
  override val sourcePsi: PsiBreakStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UBreakExpression {
  override val label: String?
    get() = sourcePsi.labelIdentifier?.text

  override val jumpTarget: UElement? by lz {
    sourcePsi.findExitedStatement().takeIf { it !== sourcePsi }?.let { JavaConverter.convertStatement(it, null) }
  }
}
