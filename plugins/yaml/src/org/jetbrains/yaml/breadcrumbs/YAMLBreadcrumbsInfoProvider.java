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
package org.jetbrains.yaml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.*;

import java.util.List;

public class YAMLBreadcrumbsInfoProvider extends BreadcrumbsInfoProvider {
  private final static Language[] LANGUAGES = new Language[]{YAMLLanguage.INSTANCE};
  
  private final static int SCALAR_MAX_LENGTH = 20;

  @Override
  public Language[] getLanguages() {
    return LANGUAGES;
  }

  @Override
  public boolean acceptElement(@NotNull PsiElement e) {
    return e instanceof YAMLScalar || e instanceof YAMLKeyValue || e instanceof YAMLSequenceItem || e instanceof YAMLDocument;
  }

  @NotNull
  @Override
  public String getElementInfo(@NotNull PsiElement e) {
    if (e instanceof YAMLDocument) {
      final YAMLFile file = (YAMLFile)e.getContainingFile();
      if (file == null) {
        return "Document";
      }
      final List<YAMLDocument> documents = file.getDocuments();
      return "Document " + getIndexOf(documents, e);
    }
    if (e instanceof YAMLKeyValue) {
      return ((YAMLKeyValue)e).getKeyText() + ':';
    }
    if (e instanceof YAMLSequenceItem) {
      final PsiElement parent = e.getParent();
      if (!(parent instanceof YAMLSequence)) {
        return "Item";
      }
      final List<YAMLSequenceItem> items = ((YAMLSequence)parent).getItems();
      return "Item " + getIndexOf(items, e);
    }
    if (e instanceof YAMLScalar) {
      return StringUtil.first(((YAMLScalar)e).getTextValue(), SCALAR_MAX_LENGTH, true);
    }
    throw new IllegalArgumentException("This element should not pass #acceptElement");
  }

  @Nullable
  @Override
  public String getElementTooltip(@NotNull PsiElement e) {
    return null;
  }
  
  @NotNull
  private static String getIndexOf(@NotNull List<?> list, Object o) {
    return String.valueOf(1 + list.indexOf(o)) + '/' + list.size();
  }
}
