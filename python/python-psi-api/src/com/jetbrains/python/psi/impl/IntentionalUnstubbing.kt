// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.AstLoadingFilter
import com.jetbrains.python.psi.impl.IntentionalUnstubbing.onFileOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Marker API to indicate places in code analysis where AST access/unstubbing is required,
 * regardless of the result of [com.jetbrains.python.psi.types.TypeEvalContext.maySwitchToAST].
 * 
 * Containing files of elements passed to [onFileOf] will be excluded from unstubbing checks in unit tests.
 * 
 * @see StubAwareComputation
 */
@ApiStatus.Internal
object IntentionalUnstubbing {
  private val forciblyUnstubbed: MutableSet<PsiFile> = ConcurrentHashMap.newKeySet()

  @JvmStatic
  fun <R> onFileOf(element: PsiElement, computable: ThrowableComputable<R, *>): R {
    val psiFile = element.containingFile
    if (ApplicationManager.getApplication().isUnitTestMode) {
      forciblyUnstubbed.add(psiFile)
    }
    // We don't rely on AstLoadingFilter behavior for code analysis at the moment, as
    // its disallowTreeLoading is not set up for inspection threads, but
    // calling forceAllowTreeLoading here will ease potential future migration to that API.
    return AstLoadingFilter.forceAllowTreeLoading(psiFile, computable)
  }

  @TestOnly
  @JvmStatic
  fun getForciblyUnstubbedFiles(): Set<PsiFile> = Collections.unmodifiableSet(forciblyUnstubbed)

  @TestOnly
  @JvmStatic
  fun resetForciblyUnstubbedFileSet() {
    forciblyUnstubbed.clear()
  }
}