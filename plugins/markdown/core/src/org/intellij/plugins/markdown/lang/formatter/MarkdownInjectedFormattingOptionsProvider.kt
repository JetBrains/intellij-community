// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.formatter

import com.intellij.formatting.InjectedFormattingOptionsProvider
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

internal class MarkdownInjectedFormattingOptionsProvider : InjectedFormattingOptionsProvider {
  override fun shouldDelegateToTopLevel(file: PsiFile): Boolean? {
    if (InjectedLanguageManager.getInstance(file.project).getInjectionHost(file) is MarkdownCodeFence) {
      return false
    }
    return null
  }
}
