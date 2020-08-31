// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.template;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiFile;
import com.intellij.sh.ShBundle;
import com.intellij.sh.psi.ShFile;
import org.jetbrains.annotations.NotNull;

public class ShContextType extends TemplateContextType {
  public ShContextType() {
    super("SHELL_SCRIPT", ShBundle.message("sh.shell.script"));
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return file instanceof ShFile;
  }
}
