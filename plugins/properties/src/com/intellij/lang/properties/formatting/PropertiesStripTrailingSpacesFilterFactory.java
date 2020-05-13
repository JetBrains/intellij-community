/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
  @NotNull
  @Override
  protected PsiBasedStripTrailingSpacesFilter createFilter(@NotNull Document document) {
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
