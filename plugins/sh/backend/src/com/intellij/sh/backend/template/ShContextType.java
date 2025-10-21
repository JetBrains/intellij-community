// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.template;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.sh.ShBundle;
import com.intellij.sh.lexer.ShTokenTypes;
import com.intellij.sh.psi.ShFile;
import org.jetbrains.annotations.NotNull;

public class ShContextType extends TemplateContextType {
  public ShContextType() {
    super(ShBundle.message("sh.shell.script"));
  }

  @Override
  public boolean isInContext(@NotNull TemplateActionContext templateActionContext) {
      PsiFile psiFile = templateActionContext.getFile();
      if (psiFile instanceof ShFile) {
        PsiElement element = psiFile.findElementAt(templateActionContext.getStartOffset());
        if (element == null) return true;
        return element.getNode().getElementType() != ShTokenTypes.COMMENT;
      }
      return false;
  }
}
