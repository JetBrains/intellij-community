// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.PsiBasedStripTrailingSpacesFilter;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

public final class PyStripTrailingSpacesFilterFactory extends PsiBasedStripTrailingSpacesFilter.Factory {
  @NotNull
  @Override
  protected PsiBasedStripTrailingSpacesFilter createFilter(@NotNull Document document) {
    return new PyStripTrailingSpacesFilter(document);
  }

  @Override
  protected boolean isApplicableTo(@NotNull Language language) {
    return language.isKindOf(PythonLanguage.INSTANCE);
  }

  private static class PyStripTrailingSpacesFilter extends PsiBasedStripTrailingSpacesFilter {
    protected PyStripTrailingSpacesFilter(@NotNull Document document) {
      super(document);
    }

    @Override
    protected void process(@NotNull PsiFile psiFile) {
      psiFile.accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyStringLiteralExpression(@NotNull PyStringLiteralExpression node) {
          if (node.isDocString()) return;
          disableRange(node.getTextRange(), false);
        }
      });
    }
  }
}
