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
package com.jetbrains.python.findUsages;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.usageView.UsageViewLongNameLocation;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyElementDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
    if (location instanceof UsageViewLongNameLocation) {
      if (element instanceof PsiNamedElement && element instanceof PyElement) {
        return ((PsiNamedElement)element).getName();
      }
    }
    return null;
  }
}
