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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.Usage;
import com.intellij.usages.rules.ImportFilteringRule;
import com.intellij.usages.rules.PsiElementUsage;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportStatementBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyImportFilteringRule extends ImportFilteringRule {
  @Override
  public boolean isVisible(@NotNull Usage usage) {
    if (usage instanceof PsiElementUsage) {
      final PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      final PsiFile containingFile = psiElement.getContainingFile();
      if (containingFile instanceof PyFile) {
        // check whether the element is in the import list
        final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(psiElement, PyImportStatementBase.class, true);
        return importStatement == null;
      }
    }
    return true;
  }
}
