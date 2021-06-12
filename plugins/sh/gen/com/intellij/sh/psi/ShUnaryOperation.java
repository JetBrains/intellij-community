// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShUnaryOperation extends ShOperation {

  @Nullable
  ShOperation getOperation();

  @Nullable
  PsiElement getMinus();

  @Nullable
  PsiElement getPlus();

}
