/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProviderBase;
import com.intellij.util.ArrayUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.CharFilter;
import org.jetbrains.annotations.NotNull;

/**
 * @author davdeev
 */
public class PathListReferenceProvider extends PsiReferenceProviderBase {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {

    PsiReference[] result = PsiReference.EMPTY_ARRAY;
    XmlValueProvider<PsiElement> provider = XmlValueProvider.getProvider(element);
    String s = provider.getValue(element);
    int offset = provider.getRangeInElement(element).getStartOffset();
    if (!s.trim().startsWith("/")) {
      return result;
    }
    int pos = -1;
    while (true) {
      int nextPos = s.indexOf(',', pos + 1);
      if (nextPos == -1) {
        PsiReference[] refs =
          createReferenceSet(element, s.substring(pos + 1), pos + offset + 1, false).getAllReferences();
        result = ArrayUtil.mergeArrays(result, refs, PsiReference.class);
        break;
      }
      else {
        PsiReference[] refs =
          createReferenceSet(element, s.substring(pos + 1, nextPos), pos + offset + 1, false).getAllReferences();
        result = ArrayUtil.mergeArrays(result, refs, PsiReference.class);
        pos = nextPos;
      }
    }

    return result;
  }

  protected FileReferenceSet createReferenceSet(PsiElement element, String s, int offset, final boolean soft) {
    int contentOffset = StringUtil.findFirst(s, CharFilter.NOT_WHITESPACE_FILTER);
    if (contentOffset >= 0) {
      offset += contentOffset;
    }
    return new FileReferenceSet(s.trim(), element, offset, this, true) {

      protected boolean isSoft() {
        return soft;
      }
    };
  }
}
