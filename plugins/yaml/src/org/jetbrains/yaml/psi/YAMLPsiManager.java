// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;
import org.jetbrains.annotations.NotNull;

final class YAMLPsiManager extends PsiTreeChangePreprocessorBase {
  YAMLPsiManager(@NotNull Project project) {
    super(project);
  }

  @Override
  protected boolean acceptsEvent(@NotNull PsiTreeChangeEventImpl event) {
    return event.getFile() instanceof YAMLFile;
  }

  @Override
  protected boolean isOutOfCodeBlock(@NotNull PsiElement element) {
    while (true) {
      if (element instanceof YAMLFile) {
        return true;
      }
      if (element instanceof PsiFile || element instanceof PsiDirectory) {
        return false;
      }
      PsiElement parent = element.getParent();
      if (!(parent instanceof YAMLFile ||
            parent instanceof YAMLKeyValue ||
            parent instanceof YAMLCompoundValue ||
            parent instanceof YAMLDocument)) {
        return false;
      }
      element = parent;
    }
  }
}
