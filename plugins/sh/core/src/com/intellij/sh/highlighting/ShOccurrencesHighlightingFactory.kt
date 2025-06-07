// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.highlighting

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class ShOccurrencesHighlightingFactory : HighlightUsagesHandlerFactoryBase() {
  override fun createHighlightUsagesHandler(editor: Editor,
                                            psiFile: PsiFile,
                                            target: PsiElement): HighlightUsagesHandlerBase<PsiElement>? {
    if (ShOccurrencesHighlightingSuppressor.isOccurrencesHighlightingEnabled(editor, psiFile)) {
      return ShOccurrencesHighlightUsagesHandler(editor, psiFile)
    }
    return null
  }
}
