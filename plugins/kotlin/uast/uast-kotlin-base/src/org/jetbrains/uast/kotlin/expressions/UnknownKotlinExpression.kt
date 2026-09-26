// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UUnknownExpression

@ApiStatus.Internal
class UnknownKotlinExpression(
  override val sourcePsi: KtExpression,
  givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UUnknownExpression {
  override val uAnnotations: List<UAnnotation>
      get() = emptyList()
}
