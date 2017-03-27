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
package com.jetbrains.python;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

public class PythonPsiManager extends PsiTreeChangePreprocessorBase {
  public PythonPsiManager(@NotNull PsiManager psiManager) {
    super(psiManager);
  }

  @Override
  protected boolean acceptsEvent(@NotNull PsiTreeChangeEventImpl event) {
    return event.getFile() instanceof PyFile;
  }

  protected boolean isOutOfCodeBlock(@NotNull PsiElement element) {
    while (true) {
      if (element instanceof PyFile) {
        return true;
      }
      if (element instanceof PsiFile || element instanceof PsiDirectory || element == null) {
        return false;
      }
      PsiElement pparent = element.getParent();
      if (pparent instanceof PyFunction) {
        PyFunction pyFunction = (PyFunction)pparent;
        return element == pyFunction.getParameterList() || element == pyFunction.getNameIdentifier();
      }
      element = pparent;
    }
  }
}
