// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShHeredoc extends ShCompositeElement {

  @Nullable
  ShCommandsList getCommandsList();

  @Nullable
  PsiElement getAmp();

  @Nullable
  PsiElement getAndAnd();

  @Nullable
  PsiElement getHeredocContent();

  @Nullable
  PsiElement getHeredocMarkerEnd();

  @NotNull
  PsiElement getHeredocMarkerStart();

  @NotNull
  PsiElement getHeredocMarkerTag();

  @Nullable
  PsiElement getOrOr();

  @Nullable
  PsiElement getPipe();

  @Nullable
  PsiElement getPipeAmp();

  @Nullable
  PsiElement getSemi();

}
