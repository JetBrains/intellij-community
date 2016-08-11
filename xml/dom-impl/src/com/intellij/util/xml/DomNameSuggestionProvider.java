/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Gregory.Shrago
 */
public class DomNameSuggestionProvider implements NameSuggestionProvider {
  @Override
  public SuggestedNameInfo getSuggestedNames(final PsiElement element, final PsiElement nameSuggestionContext, final Set<String> result) {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaData psiMetaData = ((PsiMetaOwner)element).getMetaData();
      if (psiMetaData instanceof DomMetaData) {
        final DomMetaData domMetaData = (DomMetaData)psiMetaData;
        final GenericDomValue value = domMetaData.getNameElement(domMetaData.getElement());
        ContainerUtil.addIfNotNull(result, getNameFromNameValue(value, true));
      }
    }
    return null;
  }

  @Nullable
  private static String getNameFromNameValue(final Object o, final boolean local) {
    if (o == null || o instanceof String) {
      return (String)o;
    }
    else if (o instanceof GenericValue) {
      final GenericValue value = (GenericValue)o;
      if (!local) {
        final Object name = value.getValue();
        if (name != null) {
          return String.valueOf(name);
        }
      }
      return value.getStringValue();
    }
    else {
      return String.valueOf(o);
    }
  }
}
