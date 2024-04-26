// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange

class DuplicateInfo<KotlinType>(
  val range: KotlinPsiRange,
  val controlFlow: ControlFlow<KotlinType>,
  val arguments: List<String>
)