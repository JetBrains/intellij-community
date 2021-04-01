package com.intellij.ml.local.models.api

import com.intellij.psi.PsiElementVisitor

interface LocalModelBuilder {
  fun onStarted()
  fun onFinished(success: Boolean)
  fun fileVisitor(): PsiElementVisitor
  fun build(): LocalModel?

  companion object {
    val DUMB_BUILDER = object : LocalModelBuilder {
      override fun onStarted() = Unit
      override fun onFinished(success: Boolean) = Unit
      override fun fileVisitor(): PsiElementVisitor = PsiElementVisitor.EMPTY_VISITOR
      override fun build(): LocalModel? = null
    }
  }
}