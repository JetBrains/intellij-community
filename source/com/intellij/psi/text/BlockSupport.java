package com.intellij.psi.text;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

public abstract class BlockSupport {
  public static BlockSupport getInstance(Project project) {
    return project.getComponent(BlockSupport.class);
  }

  public abstract void reparseRange(PsiFile file, int startOffset, int endOffset, @NonNls String newText) throws IncorrectOperationException;
  public abstract void reparseRange(PsiFile file, int startOffset, int endOffset, int lengthShift, char[] newText) throws IncorrectOperationException;

  public static final Key<ASTNode> TREE_TO_BE_REPARSED = new Key<ASTNode>("TREE_TO_BE_REPARSED");

  public static class ReparsedSuccessfullyException extends RuntimeException {
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }
}
