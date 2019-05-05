package com.intellij.sh.template;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiFile;
import com.intellij.sh.psi.ShFile;
import org.jetbrains.annotations.NotNull;

public class ShContextType extends TemplateContextType {
  public ShContextType() {
    super("SHELL_SCRIPT", "Shell Script");
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return file instanceof ShFile;
  }
}
