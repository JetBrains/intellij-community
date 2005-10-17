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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;

public class MoveRenameUsageInfo extends UsageInfo{
  public final PsiElement referencedElement;
  public final PsiReference reference;

  public MoveRenameUsageInfo(PsiElement element, PsiReference reference, PsiElement referencedElement){
    super(element);
    this.referencedElement = referencedElement;
    if (reference == null) {
      this.reference = element.getReference();
    }
    else {
      this.reference = reference;
    }
  }

  public MoveRenameUsageInfo(PsiElement element, PsiReference reference, int startOffset, int endOffset, PsiElement referencedElement, boolean nonCodeUsage){
    super(element, startOffset, endOffset, nonCodeUsage);
    this.referencedElement = referencedElement;
    if (reference == null) {
      this.reference = element.getReference();
    }
    else {
      this.reference = reference;
    }
  }
}
