// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashShellCommand extends BashCommand {

  @NotNull
  List<BashCompoundList> getCompoundListList();

  @Nullable
  BashFunctionDef getFunctionDef();

  @Nullable
  BashSubshell getSubshell();

  @Nullable
  PsiElement getDo();

  @Nullable
  PsiElement getDone();

  @Nullable
  PsiElement getUntil();

  @Nullable
  PsiElement getWhile();

}
