package com.intellij.psi.text;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

public abstract class BlockSupport {
  public static BlockSupport getInstance(Project project) {
    return project.getComponent(BlockSupport.class);
  }

  public abstract void reparseRange(PsiFile file, int startOffset, int endOffset, String newText) throws IncorrectOperationException;
  public abstract void reparseRange(PsiFile file, int startOffset, int endOffset, int lengthShift, char[] newText) throws IncorrectOperationException;
}
