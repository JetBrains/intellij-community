package com.intellij.python.ruff.codeinsight

import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext

inline fun <reified T : PsiElement> PsiElementPattern.Capture<out PsiElement>.withParent(): PsiElementPattern.Capture<PsiElement> =
  withParent(T::class.java) as PsiElementPattern.Capture<PsiElement>


inline fun <reified T : PsiElement> psiElement(): PsiElementPattern.Capture<T> =
  PlatformPatterns.psiElement(T::class.java)

inline fun <reified T : PsiElement> PsiElementPattern.Capture<T>.with(
  debugMethodName: String? = null,
  crossinline accept: PatternCondition<T>.(T, ProcessingContext?) -> Boolean
): PsiElementPattern.Capture<T> =
  with(object : PatternCondition<T>(debugMethodName) {
    override fun accepts(t: T, context: ProcessingContext?) = accept(t, context)
  })
