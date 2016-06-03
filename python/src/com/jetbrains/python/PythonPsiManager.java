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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

public class PythonPsiManager extends PsiTreeChangePreprocessorBase {
  public PythonPsiManager(@NotNull Project project) {
    super(project);
  }

  public void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
    if (event.getFile() instanceof PyFile) {
      super.treeChanged(event);
    }
  }

  protected boolean isInsideCodeBlock(PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return false;
    }

    if (element == null || !element.isValid() || element.getParent() == null) return true;

    while (true) {
      if (element instanceof PyFile) {
        return false;
      }
      if (element instanceof PsiFile || element instanceof PsiDirectory || element == null) {
        return true;
      }
      PsiElement pparent = element.getParent();
      if (pparent instanceof PyFunction) {
        final PyFunction pyFunction = (PyFunction)pparent;
        return !(element == pyFunction.getParameterList() || element == pyFunction.getNameIdentifier());
      }
      element = pparent;
    }
  }
}
