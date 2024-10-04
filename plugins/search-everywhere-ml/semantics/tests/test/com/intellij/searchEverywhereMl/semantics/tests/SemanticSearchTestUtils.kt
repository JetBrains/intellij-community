package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiElement
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future


fun extractPsiElement(element: PsiItemWithSimilarity<*>): PsiElement? {
  return when (val value = element.value) {
    is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> value.item
    is PsiElement -> value
    else -> null
  }
}

fun <T> runOnEdt(f: () -> Future<T>): Deferred<T> {
  val future = runBlocking(Dispatchers.EDT) { f() }
  return (future as CompletionStage<T>).asDeferred()
}
