// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.PsiBasedStripTrailingSpacesFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;

public class PropertiesStripTrailingSpacesFilterFactory extends PsiBasedStripTrailingSpacesFilter.Factory {
  @Override
  protected @NotNull PsiBasedStripTrailingSpacesFilter createFilter(@NotNull Document document) {
    return new PsiBasedStripTrailingSpacesFilter(document) {
      @Override
      protected void process(@NotNull PsiFile psiFile) {
        new PsiRecursiveElementVisitor() {
          @Override
          public void visitElement(@NotNull PsiElement element) {
            if (element instanceof PropertyImpl) {
              final ASTNode valueNode = ((PropertyImpl)element).getValueNode();
              if (valueNode != null) {
                disableRange(valueNode.getTextRange(), true);
              }
            }
            super.visitElement(element);
          }
        }.visitElement(psiFile);
      }
    };
  }

  @Override
  protected boolean isApplicableTo(@NotNull Language language) {
    return language.is(PropertiesLanguage.INSTANCE);
  }
}
