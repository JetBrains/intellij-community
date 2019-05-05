package com.intellij.bash.template;

import com.intellij.bash.psi.ShFile;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiFile;
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
