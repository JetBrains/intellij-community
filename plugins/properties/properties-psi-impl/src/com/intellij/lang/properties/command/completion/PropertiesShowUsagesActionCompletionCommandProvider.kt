package com.intellij.lang.properties.command.completion

import com.intellij.codeInsight.completion.command.commands.AbstractShowUsagesActionCompletionCommandProvider
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.psi.PsiElement

class PropertiesShowUsagesActionCompletionCommandProvider : AbstractShowUsagesActionCompletionCommandProvider() {
  override fun hasToShow(element: PsiElement): Boolean {
    return element is PropertyKeyImpl
  }
}