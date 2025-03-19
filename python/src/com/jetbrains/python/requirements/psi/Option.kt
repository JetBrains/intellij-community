// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.psi

import com.intellij.psi.PsiElement

interface Option : PsiElement {
  val constraintReq: ConstraintReq?

  val editableReq: EditableReq?

  val extraIndexUrlReq: ExtraIndexUrlReq?

  val findLinksReq: FindLinksReq?

  val indexUrlReq: IndexUrlReq?

  val noBinaryReq: NoBinaryReq?

  val noIndexReq: NoIndexReq?

  val onlyBinaryReq: OnlyBinaryReq?

  val preReq: PreReq?

  val preferBinaryReq: PreferBinaryReq?

  val referReq: ReferReq?

  val requireHashesReq: RequireHashesReq?

  val trustedHostReq: TrustedHostReq?

  val useFeatureReq: UseFeatureReq?
}