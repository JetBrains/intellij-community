package com.intellij.ml.local.models.api

import com.intellij.psi.PsiElementVisitor

interface LocalModelBuilder {
  fun onStarted()
  fun onFinished()
  fun fileVisitor(): PsiElementVisitor
  fun build(): LocalModel?

  companion object {
    val DUMB_BUILDER = object : LocalModelBuilder {
      override fun onStarted() = Unit
      override fun onFinished() = Unit
      override fun fileVisitor(): PsiElementVisitor = PsiElementVisitor.EMPTY_VISITOR
      override fun build(): LocalModel? = null
    }
  }
}