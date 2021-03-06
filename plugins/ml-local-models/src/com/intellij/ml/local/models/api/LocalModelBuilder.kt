package com.intellij.ml.local.models.api

import com.intellij.psi.PsiElementVisitor

interface LocalModelBuilder {
  fun onStarted()
  fun onFinished()
  fun fileVisitor(): PsiElementVisitor
  fun build(): LocalModel?
}