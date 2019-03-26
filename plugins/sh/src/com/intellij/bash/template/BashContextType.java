package com.intellij.bash.template;

import com.intellij.bash.psi.BashFile;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class BashContextType extends TemplateContextType {
  public BashContextType() {
    super("BASH", "Bash");
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return file instanceof BashFile;
  }
}
