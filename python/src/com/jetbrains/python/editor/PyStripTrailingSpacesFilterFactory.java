/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.PsiBasedStripTrailingSpacesFilter;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

public class PyStripTrailingSpacesFilterFactory extends PsiBasedStripTrailingSpacesFilter.Factory {
  @NotNull
  @Override
  protected PsiBasedStripTrailingSpacesFilter createFilter(@NotNull Document document) {
    return new PyStripTrailingSpacesFilter(document);
  }

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
        public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
          if (node.isDocString()) return;
          disableRange(node.getTextRange(), false);
        }
      });
    }
  }
}
