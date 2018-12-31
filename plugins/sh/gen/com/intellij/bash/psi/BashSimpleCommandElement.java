// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashSimpleCommandElement extends BashCompositeElement {

  @Nullable
  BashAssignmentWordRule getAssignmentWordRule();

  @Nullable
  BashRedirection getRedirection();

  @Nullable
  BashString getString();

  @Nullable
  PsiElement getInt();

  @Nullable
  PsiElement getNumber();

  @Nullable
  PsiElement getVariable();

  @Nullable
  PsiElement getWord();

}
