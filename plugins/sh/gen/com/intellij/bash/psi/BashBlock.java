// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashBlock extends BashCompositeElement {

  @Nullable
  BashCompoundList getCompoundList();

  @Nullable
  PsiElement getLeftCurly();

  @Nullable
  PsiElement getRightCurly();

}
