// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashSimpleCommandElement extends BashCompositeElement {

  @Nullable
  BashRedirection getRedirection();

  @Nullable
  BashString getString();

  @Nullable
  PsiElement getAssignmentWord();

  @Nullable
  PsiElement getWord();

}
