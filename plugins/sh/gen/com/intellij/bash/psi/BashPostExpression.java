// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashPostExpression extends BashExpression {

  @NotNull
  BashExpression getExpression();

  @Nullable
  PsiElement getMinusMinus();

  @Nullable
  PsiElement getPlusPlus();

}
