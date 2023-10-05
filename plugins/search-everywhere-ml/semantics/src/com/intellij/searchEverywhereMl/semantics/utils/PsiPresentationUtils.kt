package com.intellij.searchEverywhereMl.semantics.utils

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.ui.JBColor

fun attachPsiPresentation(
  element: PsiElement,
  elementRenderer: SearchEverywherePsiRenderer
): PsiItemWithPresentation {
  var presentation = elementRenderer.computePresentation(element)
  if (Registry.`is`("search.everywhere.ml.semantic.highlight.items")) {
    presentation = TargetPresentation.builder(presentation).backgroundColor(JBColor.GREEN.darker().darker()).presentation()
  }
  return PsiItemWithPresentation(element, presentation)
}