// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

interface TypeEvalContextFactory {
  fun codeCompletion(project: Project, origin: PsiFile?): TypeEvalContext
  fun userInitiated(project: Project, origin: PsiFile?): TypeEvalContext
  fun codeAnalysis(project: Project, origin: PsiFile?): TypeEvalContext
  fun codeInsightFallback(project: Project?): TypeEvalContext
  fun deepCodeInsight(project: Project): TypeEvalContext

  companion object {
    fun getInstance(): TypeEvalContextFactory = service()
  }
}