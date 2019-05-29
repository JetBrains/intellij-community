// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShFunctionDefinition extends ShCommand {

  @Nullable
  ShBlock getBlock();

  @Nullable
  ShFunctionName getFunctionName();

  @Nullable
  PsiElement getLeftParen();

  @Nullable
  PsiElement getRightParen();

  @Nullable
  PsiElement getFunction();

}
