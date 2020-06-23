// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShSimpleCommandElement extends ShCompositeElement {

  @Nullable
  ShArithmeticExpansion getArithmeticExpansion();

  @Nullable
  ShBraceExpansion getBraceExpansion();

  @Nullable
  ShCommand getCommand();

  @Nullable
  ShLiteral getLiteral();

  @Nullable
  ShOldArithmeticExpansion getOldArithmeticExpansion();

  @Nullable
  ShRedirection getRedirection();

  @Nullable
  ShShellParameterExpansion getShellParameterExpansion();

  @Nullable
  ShVariable getVariable();

  @Nullable
  PsiElement getBang();

  @Nullable
  PsiElement getDollar();

  @Nullable
  PsiElement getFiledescriptor();

}
