/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.refactoring.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;

public class NonCodeUsageInfo extends MoveRenameUsageInfo{
  public final String newText;

  private NonCodeUsageInfo(PsiElement element, int startOffset, int endOffset, PsiElement referencedElement, String newText){
    super(element, null, startOffset, endOffset, referencedElement, true);
    this.newText = newText;
  }

  @Nullable
  public static NonCodeUsageInfo create(PsiFile file,
                                        int startOffset,
                                        int endOffset,
                                        PsiElement referencedElement,
                                        String newText) {
    PsiElement element = file.findElementAt(startOffset);
    while(element != null){
      TextRange range = element.getTextRange();
      if (range.getEndOffset() < endOffset){
        element = element.getParent();
      }
      else{
        break;
      }
    }

    if (element == null) return null;

    int elementStart = element.getTextRange().getStartOffset();
    startOffset -= elementStart;
    endOffset -= elementStart;
    return new NonCodeUsageInfo(element, startOffset, endOffset, referencedElement, newText);
  }

  @Nullable
  public PsiReference getReference() {
    return null;
  }
}
