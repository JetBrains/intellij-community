// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;

public interface ShFunctionDefinition extends ShCommand, PsiNameIdentifierOwner {

  @Nullable
  ShBlock getBlock();

  @Nullable
  ShParenthesesBlock getParenthesesBlock();

  @Nullable
  PsiElement getLeftParen();

  @Nullable
  PsiElement getRightParen();

  @Nullable
  PsiElement getFunction();

  @Nullable
  PsiElement getWord();

}
